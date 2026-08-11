"""Remove auto_start_remaining / prepare_remaining_before from crawl_maker."""
from __future__ import annotations

from pathlib import Path

p = Path(__file__).resolve().parents[1] / "data_pipeline" / "megazip" / "crawl.py"
text = p.read_text(encoding="utf-8")

old_sig = '''async def crawl_maker(
    paths: MakerPaths,
    config: MegazipConfig,
    *,
    max_pages: int | None = None,
    priority_chassis: frozenset[str] | None = None,
    skip_crawl: bool = False,
    priority_model_seeds: tuple[str, ...] = (),
    prepare_remaining_before: frozenset[str] | None = None,
    auto_start_remaining: bool = False,
    model_slugs: frozenset[str] | None = None,
    worker_mode: bool = False,
    worker_id: str | None = None,
    rate_limit_seconds: float | None = None,
) -> dict[str, Any]:
    """Crawl Megazip pages into cache.

    ``worker_mode`` — drain PENDING pages only for leased ``model_slugs``.
    Skips hub/seed enqueue, self-heal, and remaining-pass prep. Acquires
    exclusive model leases so parallel workers and the main crawler never
    claim the same model.

    ``auto_start_remaining`` — when the main crawler's priority-filtered queue
    drains (and no worker leases remain), call ``prepare_remaining_crawl`` and
    continue without a chassis filter so the rest of the maker is crawled in
    the same process. Also runs one underexplored-model ensure before exit.
    """
'''

new_sig = '''async def crawl_maker(
    paths: MakerPaths,
    config: MegazipConfig,
    *,
    max_pages: int | None = None,
    priority_chassis: frozenset[str] | None = None,
    skip_crawl: bool = False,
    priority_model_seeds: tuple[str, ...] = (),
    model_slugs: frozenset[str] | None = None,
    worker_mode: bool = False,
    worker_id: str | None = None,
    rate_limit_seconds: float | None = None,
) -> dict[str, Any]:
    """Crawl Megazip pages into cache.

    ``worker_mode`` — drain PENDING pages only for leased ``model_slugs``.
    Skips hub/seed enqueue and self-heal. Acquires exclusive model leases so
    parallel workers and the main crawler never claim the same model.
    """
'''
if old_sig not in text:
    raise SystemExit("crawl_maker signature not found")
text = text.replace(old_sig, new_sig)

old_vars = '''    heal_stats: dict[str, int] = {}
    requeue_stats: dict[str, int] = {}
    leased_models: frozenset[str] = frozenset()
    wid = worker_id or ("worker-" + "-".join(sorted(model_slugs or []))[:80])
    active_priority = priority_chassis
    remaining_started = False
    coverage_ensured = False
'''
new_vars = '''    heal_stats: dict[str, int] = {}
    requeue_stats: dict[str, int] = {}
    leased_models: frozenset[str] = frozenset()
    wid = worker_id or ("worker-" + "-".join(sorted(model_slugs or []))[:80])
    active_priority = priority_chassis
'''
if old_vars not in text:
    raise SystemExit("crawl vars not found")
text = text.replace(old_vars, new_vars)

old_prep = '''        if prepare_remaining_before is not None:
            requeue_stats = prepare_remaining_crawl(
                paths,
                priority_chassis=prepare_remaining_before,
            )

        start_url = config.hub_url(paths.maker)
'''
new_prep = '''        start_url = config.hub_url(paths.maker)
'''
if old_prep not in text:
    raise SystemExit("prepare_remaining_before block not found")
text = text.replace(old_prep, new_prep)

# Replace empty-queue handoff with simple break
old_handoff = '''                    if left == 0:
                        leased_left = leased_pending_count(paths.state_db)
                        if leased_left > 0:
                            leases = list_active_leases(paths.state_db)
                            logger.info(
                                "[%s] waiting on %s leased PENDING pages (%s workers)",
                                paths.maker,
                                leased_left,
                                len(set(leases.values())),
                            )
                            await asyncio.sleep(5.0)
                            continue
                        if (
                            auto_start_remaining
                            and active_priority is not None
                            and not remaining_started
                        ):
                            handoff = prepare_remaining_crawl(
                                paths,
                                priority_chassis=active_priority,
                            )
                            requeue_stats = {
                                **requeue_stats,
                                **{f"handoff_{k}": v for k, v in handoff.items()},
                            }
                            remaining_started = True
                            active_priority = None
                            logger.info(
                                "[%s] priority models done — starting remaining "
                                "Nissan models (%s)",
                                paths.maker,
                                handoff,
                            )
                            continue
                        if auto_start_remaining and not coverage_ensured:
                            coverage_ensured = True
                            done_chassis = load_visited_deep_chassis(paths.state_db)
                            ensure = prepare_remaining_crawl(
                                paths,
                                priority_chassis=done_chassis,
                            )
                            requeue_stats = {
                                **requeue_stats,
                                **{f"ensure_{k}": v for k, v in ensure.items()},
                            }
                            queued = (
                                int(ensure.get("models_reset") or 0)
                                + int(ensure.get("variants") or 0)
                                + int(ensure.get("sections") or 0)
                            )
                            if queued > 0:
                                logger.info(
                                    "[%s] queued underexplored remaining models (%s)",
                                    paths.maker,
                                    ensure,
                                )
                                continue
                        break
'''
new_handoff = '''                    if left == 0:
                        leased_left = leased_pending_count(paths.state_db)
                        if leased_left > 0:
                            leases = list_active_leases(paths.state_db)
                            logger.info(
                                "[%s] waiting on %s leased PENDING pages (%s workers)",
                                paths.maker,
                                leased_left,
                                len(set(leases.values())),
                            )
                            await asyncio.sleep(5.0)
                            continue
                        break
'''
if old_handoff not in text:
    raise SystemExit("handoff block not found")
text = text.replace(old_handoff, new_handoff)

for needle in ("auto_start_remaining", "prepare_remaining_before", "remaining_started", "coverage_ensured"):
    if needle in text:
        raise SystemExit(f"still contains {needle}")

p.write_text(text, encoding="utf-8")
print("patched", p)

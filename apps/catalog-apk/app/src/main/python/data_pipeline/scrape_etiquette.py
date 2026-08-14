"""Backward-compatible re-exports — implementation lives in amayama_catalog_auto. """

from data_pipeline.amayama_catalog_auto import (  # noqa: F401
    DEFAULT_USER_AGENT,
    RateLimiter,
    ResponseCache,
    RobotsGate,
    ScrapeConfig,
    backoff_delay,
    retry_async,
)

# Local Windows Setup + Real-Time Repo Sync

This Cloud Agent **cannot write** to `C:\Users\j\Desktop\nissan gtr`.
Run the commands below **once on your Windows PC** in PowerShell or Cursor's terminal.

---

## One-time: copy the cloud project to your Desktop folder

Open **PowerShell** and run:

```powershell
# Destination (matches your path)
$dest = "C:\Users\j\Desktop\nissan gtr"

# Clone the orchestration branch into that folder
if (Test-Path $dest) {
  if (Test-Path "$dest\.git") {
    Write-Host "Repo already exists at $dest — pulling latest..."
    Set-Location $dest
    git fetch origin
    git checkout cursor/erp-cursor-setup-ad25
    git pull origin cursor/erp-cursor-setup-ad25
  } else {
    Write-Host "Folder exists but is not a git repo. Cloning into a temp name, then merging..."
    $tmp = "$dest\_clone_tmp"
    git clone -b cursor/erp-cursor-setup-ad25 https://github.com/Vanguduza/Nissan-Gtr-Auto.git $tmp
    Get-ChildItem $tmp -Force | Move-Item -Destination $dest -Force
    Remove-Item $tmp -Recurse -Force
    Set-Location $dest
  }
} else {
  git clone -b cursor/erp-cursor-setup-ad25 https://github.com/Vanguduza/Nissan-Gtr-Auto.git $dest
  Set-Location $dest
}

Write-Host "Done. Open this folder in Cursor Desktop:"
Write-Host "  File → Open Folder → $dest"
```

Or double-click / run the script already in the repo after the first clone:

```powershell
.\scripts\windows\clone-to-desktop.ps1
```

Then in **Cursor Desktop**: **File → Open Folder** → `C:\Users\j\Desktop\nissan gtr`

---

## Real-time sync to GitHub

Two modes are included under `scripts/windows/`:

| Script | What it does |
|--------|----------------|
| `auto-sync.ps1` | Watches the folder; on any change, waits briefly, then `git add` + `commit` + `push` |
| `start-auto-sync.bat` | Double-click launcher for the watcher |

### Start auto-sync

```powershell
cd "C:\Users\j\Desktop\nissan gtr"
.\scripts\windows\auto-sync.ps1
```

Leave that PowerShell window open while you work. Edits are pushed to `origin` on the current branch within a few seconds of each save burst.

### Stop auto-sync

Press `Ctrl+C` in that window, or close it.

### Safety defaults

- Only tracks the **current git branch** (default after clone: `cursor/erp-cursor-setup-ad25`)
- Debounces ~3 seconds so rapid saves become one commit
- Skips if there is nothing to commit
- Commit message: `auto-sync: YYYY-MM-DD HH:MM:SS`
- Does **not** force-push

### Optional: sync on a schedule instead of a watcher

```powershell
# Every 60 seconds — useful if file watcher is blocked by antivirus
.\scripts\windows\auto-sync.ps1 -PollSeconds 60
```

---

## After setup checklist

- [ ] Folder `C:\Users\j\Desktop\nissan gtr` contains the full git repo
- [ ] Opened in Cursor Desktop (not a Cloud Agent)
- [ ] `auto-sync.ps1` is running in a PowerShell window
- [ ] Make a small edit, save, wait ~5s, confirm push on GitHub
- [ ] Archive the cloud agent when done: https://cursor.com/agents/bc-b00ea338-fc33-4a85-a69a-3d4ae59ead25

---

## Notes

- **GitHub auth**: first `git push` may ask you to sign in (GitHub CLI or credential manager).
- **Do not** run auto-sync on `main` unless you intend to push straight to main; prefer a feature branch.
- Auto-sync creates many small commits — fine for solo WIP; squash when opening a clean PR if you prefer.

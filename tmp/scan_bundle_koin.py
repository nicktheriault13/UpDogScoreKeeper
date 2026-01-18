from pathlib import Path

p = Path(r"C:\UpDogScoreKeeper\composeApp\build\dist\js\productionExecutable\composeApp.js")
text = p.read_text(encoding="utf-8", errors="ignore")
needle = "KoinApplication"
print("FOUND" if needle in text else "NOT_FOUND")
if needle in text:
    idx = text.index(needle)
    start = max(0, idx - 200)
    end = min(len(text), idx + 400)
    snippet = text[start:end]
    Path(r"C:\UpDogScoreKeeper\tmp\koin_snippet.txt").write_text(snippet, encoding="utf-8")

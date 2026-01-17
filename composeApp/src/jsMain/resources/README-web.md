# Web / PWA build

This module is configured to build a Kotlin/JS (IR) browser app and can be installed as a PWA.

## Dev run

Starting the dev server will print a local URL (usually `http://localhost:8080`).

## Production build

The production web assets are generated under:

- `composeApp/build/dist/js/productionExecutable/`

That folder contains `index.html`, `composeApp.js`, `manifest.webmanifest`, `service-worker.js`, and assets.

## GitHub Pages deployment

This repo includes a GitHub Actions workflow:

- `.github/workflows/deploy-gh-pages.yml`

It builds `:composeApp:jsBrowserDistribution` and publishes `composeApp/build/dist/js/productionExecutable` to GitHub Pages.

### One-time repo setup

1. In GitHub:
   - **Settings → Pages**
   - **Build and deployment → Source: GitHub Actions**
2. Push to `main`, or run the workflow manually from **Actions**.

## Notes

- Web support is currently **best-effort**: file import/export, ZIP parsing, and native audio playback are stubbed in `src/jsMain` so the app compiles and runs.
- If you want full feature parity in the browser, the next step is implementing those stubs using browser APIs (File System Access / `<input type=file>` / WebAudio).

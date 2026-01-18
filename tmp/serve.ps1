Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Serve the repo root so smoke.html can reference the built JS bundle
python -m http.server 8080

# Portfolio GitHub API
Java 21 / Maven / Spring Boot 3.5.7

PowerShell:
```powershell
$env:GITHUB_OWNER="makoto-toyohashi"
$env:GITHUB_REPOSITORIES="portfolio-frontend,portfolio-backend"
$env:GITHUB_TOKEN="YOUR_GITHUB_TOKEN"
$env:GEMINI_API_KEY="YOUR_GEMINI_API_KEY"
$env:GEMINI_PROJECT_ID="YOUR_GEMINI_PROJECT_ID"
$env:GEMINI_PROJECT_NUMBER="YOUR_GEMINI_PROJECT_NUMBER"
$env:GEMINI_MODEL="GEMINI_MODEL"
$env:GEMINI_EMBEDDING_MODEL="GEMINI_EMBEDDING_MODEL"
# Optional: $env:GITHUB_TOKEN="your-token"
mvn spring-boot:run
```
Only explicitly listed public repositories are exposed. Token optional; use read-only fine-grained permissions (Contents, Issues, Pull requests) if needed. Never commit token. Cache 5 minutes. No database required.

- GET http://localhost:8080/api/v1/github/projects
- GET http://localhost:8080/api/v1/github/projects/portfolio-frontend/progress

Issue completion includes all closed issues (including not planned); PRs excluded. Commits: default branch, last 30 UTC calendar days. Languages: bytes. No issues => null progress. Latest release: tag only. No authentication on public endpoints; add rate limiting before deployment. Next.js should fetch server-side. Maven build not tested in generation environment.

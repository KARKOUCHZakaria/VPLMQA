# Automatic deployment to the test VM

This script prepares a separate `vplmqa-vm` Docker Compose project on Windows or
Linux. It is intended for an isolated test VM, not public production hosting.
It does not transfer existing PostgreSQL/MinIO data or configure the VM firewall.

## First deployment

1. Copy or clone the project onto the VM. The E2E agents do not require `ml/*.pkl`.
2. Install Python 3.9+ and Docker with Compose supporting `include` and `--wait`.
3. Check that ports 3000, 8080, 8090 and the infrastructure ports are not occupied
   by another VPLMQA stack. Do not run the local Windows launcher on this VM.
4. From the repository root, run:

```sh
python scripts/deploy-vm.py --public-url http://10.0.0.50:3000
```

Replace the IP with the VM address reachable from colleagues' browsers. On Linux,
the command may be `python3`. The key is prompted without echoing it. To supply a
new key, use `--llm-key-file /path/to/protected-key.txt`; do not pass a key as a
command-line argument. An existing `secrets/mistral_api_key.txt` is reused.

The script generates VM passwords, configures Docker service addresses, builds
Java with a Java 21 Maven container, builds the frontend and agents, and checks
readiness. Browser requests, MinIO images and E2E screenshots use the frontend
origin through Nginx. E2E tests use Chromium inside the agents container.

To generate settings without starting services, add `--prepare-only`. To deploy
again, run the same command. Generated passwords are preserved. Use
`--skip-java-build` only when the current JARs have already been built.

Generated files are `.deployment/vm.env`, `.deployment/compose.vm.json`,
`front-end/.vm-nginx.conf` and `secrets/mistral_api_key.txt`; keep them private.
The existing root `.env` is left intact, or a placeholder is created if absent.
On Windows, restrict access to these files using the VM account's file permissions.

## Still required

- Configure Azure DevOps organization/project/PAT in Settings and refresh members.
- Configure Figma projects and test-account secrets in the application.
- Verify the VM can reach the application under test and external APIs, including
  any required VPN/proxy/certificates. Confirm Mistral quota with a real workflow.
- Test login, an E2E scenario, a failure screenshot, and Azure ticket creation
  from a colleague's browser. Automatic health checks do not prove these workflows.
- Plan database and MinIO backup/restore separately if existing PC data is needed.
- The current Vault is in development mode: dynamically entered secrets can be
  lost when Vault is recreated. Back them up securely or configure persistent Vault
  before relying on this environment. Mistral is restored by `vault-init`.
- Limit network access to the test team. HTTPS, persistent Vault and public hosting
  require additional configuration; this helper accepts HTTP on port 3000.

For diagnostics, use the generated configuration, not the local Compose file:

```sh
docker compose --env-file .deployment/vm.env -f docker-compose.yml -f .deployment/compose.vm.json ps
docker compose --env-file .deployment/vm.env -f docker-compose.yml -f .deployment/compose.vm.json logs --tail 100 gateway langgraph-agents
```

Do not delete `.deployment/vm.env` on an existing deployment: regenerating database
passwords does not change the credentials stored in an existing PostgreSQL volume.

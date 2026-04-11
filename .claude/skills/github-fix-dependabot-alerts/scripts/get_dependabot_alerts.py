# /// script
# requires-python = ">=3.11"
# ///

import subprocess
import json
import sys

def fetch_dependabot_alerts():
    try:
        # Use `gh api` to call the Dependabot REST API endpoint directly.
        # :owner and :repo are automatically resolved by the CLI based on the local Git context.
        endpoint = "repos/:owner/:repo/dependabot/alerts?state=open"

        result = subprocess.run(
            ["gh", "api", endpoint],
            capture_output=True,
            text=True,
            check=True,
            encoding="utf-8"
        )

        raw_alerts = json.loads(result.stdout)
        clean_alerts = []

        for alert in raw_alerts:
            vuln = alert.get("security_vulnerability", {})
            adv = alert.get("security_advisory", {})
            pkg = vuln.get("package", {})
            patch = vuln.get("first_patched_version", {})

            clean_alerts.append({
                "alert_number": alert.get("number"),
                "package_name": pkg.get("name"),
                "ecosystem": pkg.get("ecosystem"),
                "severity": vuln.get("severity"),
                "summary": adv.get("summary"),
                "patched_version": patch.get("identifier", "No official patch available"),
                "url": alert.get("html_url")
            })

        # Print a clean JSON to standard output for Claude
        print(json.dumps({"open_dependabot_alerts": clean_alerts}, indent=2))

    except subprocess.CalledProcessError as e:
        error_msg = {
            "error": "Unable to fetch Dependabot alerts.",
            "details": e.stderr.strip(),
            "hint": "Ensure you have the 'security_events' permission on your GitHub token and Dependabot is enabled."
        }
        print(json.dumps(error_msg))
        sys.exit(1)
    except Exception as e:
        print(json.dumps({"error": f"Unexpected error: {str(e)}"}))
        sys.exit(1)

if __name__ == "__main__":
    fetch_dependabot_alerts()

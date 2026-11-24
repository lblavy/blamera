"""Query the public OSV API for the resolved Android runtime inventory."""

from datetime import datetime, timezone
import json
from urllib.request import Request, urlopen
from urllib.parse import quote

from quality_gates import QualityError


def request_json(url, payload=None):
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    request = Request(url, data=data, headers={"Content-Type": "application/json", "User-Agent": "Blamera-quality/1"})
    with urlopen(request, timeout=45) as response:
        return json.load(response)


def scan_dependencies(modules, transport=None):
    transport = transport or request_json
    if not modules:
        raise QualityError("Empty runtime inventory")
    report = {"status": "complete", "scope": "Android debugRuntimeClasspath, direct and transitive Maven artifacts",
              "queried_at": datetime.now(timezone.utc).isoformat(), "packages": modules,
              "findings": [], "withdrawn": []}
    response = transport("https://api.osv.dev/v1/querybatch", {"queries": [
        {"package": {"ecosystem": m["ecosystem"], "name": m["name"]}, "version": m["version"]}
        for m in modules]})
    results = response.get("results")
    if not isinstance(results, list) or len(results) != len(modules):
        raise QualityError("OSV batch response count does not match runtime inventory")
    for module, result in zip(modules, results):
        if result.get("next_page_token"):
            raise QualityError("OSV response is paginated: scan blocked, additional results not discarded")
        for vulnerability in result.get("vulns", []):
            identifier = vulnerability["id"]
            details = transport("https://api.osv.dev/v1/vulns/" + quote(identifier, safe=""))
            if details.get("id") != identifier:
                raise QualityError("OSV advisory details missing or inconsistent")
            finding = {"id": identifier, "package": module,
                       "severity": details.get("database_specific", {}).get("severity", "UNKNOWN"),
                       "details": details}
            report["withdrawn" if details.get("withdrawn") else "findings"].append(finding)
    return report

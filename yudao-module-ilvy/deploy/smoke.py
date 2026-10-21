"""Read-only isolated deployment smoke; no account creation or personal state writes."""
import json
import os
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:48082/app-api/"
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))

def call(path, method="GET", data=None, token=None):
    headers = {"tenant-id": "1", "Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(BASE + path, headers=headers, method=method,
        data=json.dumps(data).encode() if data is not None else None)
    try:
        response = opener.open(request, timeout=15)
    except urllib.error.HTTPError as error:
        response = error
    return json.load(response)

def check(name, condition):
    if not condition:
        print("FAIL", name)
        raise RuntimeError(name)
    print("PASS", name)

try:
    capabilities = call("ilvy/capabilities")
    check("capability endpoint", capabilities.get("code") == 0)
    check("external services remain unconfigured", all(not capabilities["data"].get(k)
        for k in ["map", "weather", "panorama", "payment", "wechat"]))
    page = call("ilvy/places/page?pageSize=2")
    check("database-backed page endpoint", page.get("code") == 0 and isinstance(page["data"]["total"], int))
    check("anonymous private-state denial", call("ilvy/state").get("code") == 401)
    check("local mock-session unavailable", call("ilvy/local-session", "POST").get("code") != 0)
    mobile, password = os.getenv("ILVY_SMOKE_MOBILE"), os.getenv("ILVY_SMOKE_PASSWORD")
    if bool(mobile) != bool(password):
        raise RuntimeError("Supply both approved smoke credential variables or neither")
    if mobile and password:
        login = call("member/auth/login", "POST", {"mobile": mobile, "password": password})
        check("existing approved member login", login.get("code") == 0)
        token = login["data"]["accessToken"]
        check("authenticated private-state read", call("ilvy/state", token=token).get("code") == 0)
        check("logout", call("member/auth/logout", "POST", token=token).get("code") == 0)
        check("revoked token denial", call("ilvy/state", token=token).get("code") == 401)
    else:
        print("NOT_TESTED authenticated member/Redis token flow: approved credentials not supplied")
except Exception as error:
    # Never print HTTP bodies, URLs, credentials, or tokens.
    print("SMOKE_FAILED", type(error).__name__)
    raise SystemExit(1)

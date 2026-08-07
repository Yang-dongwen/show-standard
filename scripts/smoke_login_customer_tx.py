# -*- coding: utf-8 -*-
"""Local smoke: login / customers / transactions for frontend-uniapp + Java API."""
from __future__ import annotations

import json
import random
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path

BASE = "http://127.0.0.1:8080"
# 云版 MySQL 店长（可用 SaaS reset-password 重置；本机冒烟默认）
USER, PASS = "zhangsan", "Smoke@123456"
ROOT = Path(__file__).resolve().parents[1]
results: list[dict] = []


def api(method: str, path: str, body=None, token=None):
    data = None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            raw = resp.read().decode("utf-8", errors="replace")
            try:
                j = json.loads(raw)
            except Exception:
                j = None
            return {"ok": True, "status": resp.status, "body": j, "raw": raw}
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace")
        try:
            j = json.loads(raw)
        except Exception:
            j = None
        return {"ok": False, "status": e.code, "body": j, "raw": raw, "error": str(e)}
    except Exception as e:
        return {"ok": False, "status": None, "body": None, "raw": "", "error": str(e)}


def add(cid: str, name: str, passed: bool, detail: str) -> None:
    results.append({"id": cid, "name": name, "pass": bool(passed), "detail": detail})
    mark = "PASS" if passed else "FAIL"
    print(f"[{mark}] {cid} {name} :: {detail[:220]}")


def trunc(s: str | None, n: int = 300) -> str:
    s = s or ""
    return s if len(s) <= n else s[:n] + "..."


def page_items(data):
    if data is None:
        return []
    if isinstance(data, list):
        return data
    if isinstance(data, dict):
        for k in ("items", "records", "list"):
            if isinstance(data.get(k), list):
                return data[k]
    return []


def file_has(rel: str, needle: str) -> bool:
    f = ROOT / "frontend-uniapp" / "src" / rel
    if not f.exists():
        return False
    return needle in f.read_text(encoding="utf-8", errors="replace")


def main() -> int:
    # reachability
    r = api("GET", "/api/auth/register-status")
    add(
        "S0",
        "后端可达 register-status",
        r.get("status") == 200 and (r.get("body") or {}).get("success") is True,
        trunc(r.get("raw")),
    )

    # bad login
    r = api("POST", "/api/auth/login", {"username": USER, "password": "wrong-password-xxx"})
    bad_ok = (not r.get("ok")) or (r.get("body") or {}).get("success") is False
    add("L0", "错误密码应失败", bad_ok, f"status={r.get('status')} {trunc(r.get('raw'), 150)}")

    # good login
    r = api("POST", "/api/auth/login", {"username": USER, "password": PASS})
    token = None
    user = None
    body = r.get("body") or {}
    data = body.get("data") or {}
    if r.get("ok") and data.get("token"):
        token = data["token"]
        user = data.get("user") or {}
        add(
            "L1",
            f"登录 {USER}",
            True,
            f"tokenLen={len(token)} role={user.get('role')} user={user.get('username')}",
        )
    else:
        add("L1", f"登录 {USER}", False, trunc(r.get("raw")))

    if token:
        r = api("GET", "/api/auth/me", token=token)
        add(
            "L2",
            "GET /api/auth/me",
            r.get("ok") and (r.get("body") or {}).get("success") is not False,
            trunc(r.get("raw")),
        )
    else:
        add("L2", "GET /api/auth/me", False, "no token")

    r = api("GET", "/api/reports/dashboard")
    add(
        "L3",
        "无 token 访问 dashboard 应 401",
        r.get("status") == 401,
        f"status={r.get('status')} {trunc(r.get('raw'), 120)}",
    )

    if token:
        r = api("GET", "/api/reports/dashboard", token=token)
        body = r.get("body") or {}
        shape = isinstance(body, dict) and "success" in body and "data" in body
        add(
            "U1",
            "响应契约 success+data",
            shape and body.get("success") is True,
            f"keys={list(body.keys())} {trunc(r.get('raw'), 150)}",
        )
        add(
            "H1",
            "经营总览 dashboard",
            r.get("ok") and body.get("success") is True,
            trunc(r.get("raw")),
        )

    cust_id = None
    phone = f"139{random.randint(10000000, 99999999)}"
    verify_code = None

    if token:
        r = api("GET", "/api/customers?page=1&size=20", token=token)
        body = r.get("body") or {}
        items = page_items(body.get("data"))
        add(
            "C1",
            "会员列表",
            r.get("ok") and body.get("success") is not False,
            f"count={len(items)} {trunc(r.get('raw'), 200)}",
        )
        if items:
            cust_id = items[0].get("id")

        r = api("POST", "/api/customers", {"name": "冒烟会员", "phone": phone}, token=token)
        body = r.get("body") or {}
        ok = r.get("ok") and body.get("success") is not False
        add("C2", "新建会员", ok, f"phone={phone} {trunc(r.get('raw'))}")
        if ok:
            d = body.get("data") or {}
            cust_id = d.get("id") or d.get("customerId") or cust_id
            verify_code = d.get("verifyCode") or d.get("checkCode")

        r = api("GET", f"/api/customers?keyword={phone}&page=1&size=10", token=token)
        body = r.get("body") or {}
        items = page_items(body.get("data"))
        found = False
        for it in items:
            if str(it.get("phone", "")) == phone or it.get("id") == cust_id:
                found = True
                cust_id = it.get("id") or cust_id
                verify_code = verify_code or it.get("verifyCode") or it.get("checkCode")
                break
        add(
            "C3",
            "搜索新建会员",
            r.get("ok") and found,
            f"found={found} verify={verify_code} {trunc(r.get('raw'), 180)}",
        )

        if cust_id:
            r = api(
                "PUT",
                f"/api/customers/{cust_id}",
                {"name": "冒烟会员改", "phone": phone},
                token=token,
            )
            body = r.get("body") or {}
            add(
                "C4",
                "编辑会员",
                r.get("ok") and body.get("success") is not False,
                trunc(r.get("raw")),
            )

    emp_id = None
    svc_id = None
    if token:
        r = api("GET", "/api/employees/options", token=token)
        opts = (r.get("body") or {}).get("data") or []
        if isinstance(opts, list) and opts:
            emp_id = opts[0].get("id")
        add("E1", "员工 options", r.get("ok"), f"emp_id={emp_id} {trunc(r.get('raw'), 120)}")
        if not emp_id:
            r = api("POST", "/api/employees", {"name": "冒烟员工"}, token=token)
            print("create emp", trunc(r.get("raw"), 200))
            r = api("GET", "/api/employees/options", token=token)
            opts = (r.get("body") or {}).get("data") or []
            if isinstance(opts, list) and opts:
                emp_id = opts[0].get("id")

        r = api("GET", "/api/config/services/options", token=token)
        opts = (r.get("body") or {}).get("data") or []
        if isinstance(opts, list) and opts:
            svc_id = opts[0].get("id")
        add("S1", "服务 options", r.get("ok"), f"svc_id={svc_id} {trunc(r.get('raw'), 120)}")
        if not svc_id:
            r = api(
                "POST",
                "/api/config/services",
                {"name": "冒烟剪发", "price": 50, "defaultPrice": 50},
                token=token,
            )
            print("create svc", trunc(r.get("raw"), 200))
            r = api("GET", "/api/config/services/options", token=token)
            opts = (r.get("body") or {}).get("data") or []
            if isinstance(opts, list) and opts:
                svc_id = opts[0].get("id")

    if token and cust_id and not verify_code:
        r = api("GET", f"/api/customers?keyword={phone}&page=1&size=5", token=token)
        items = page_items((r.get("body") or {}).get("data"))
        if items:
            verify_code = items[0].get("verifyCode") or items[0].get("checkCode")

    if token and cust_id:
        r = api(
            "POST",
            "/api/transactions/recharge",
            {"customerId": cust_id, "amount": 100, "remark": "冒烟充值"},
            token=token,
        )
        body = r.get("body") or {}
        add(
            "T1",
            "充值 100",
            r.get("ok") and body.get("success") is not False,
            f"cust={cust_id} {trunc(r.get('raw'))}",
        )
    else:
        add("T1", "充值 100", False, f"token={bool(token)} cust={cust_id}")

    if token and cust_id:
        payload = {"customerId": cust_id, "amount": 30, "remark": "冒烟消费"}
        if emp_id:
            payload["employeeId"] = emp_id
        if svc_id:
            payload["serviceTypeId"] = svc_id
        if verify_code:
            payload["verifyCode"] = str(verify_code)
        r = api("POST", "/api/transactions/consume", payload, token=token)
        body = r.get("body") or {}
        add(
            "T2",
            "消费 30",
            r.get("ok") and body.get("success") is not False,
            f"emp={emp_id} svc={svc_id} code={verify_code} {trunc(r.get('raw'))}",
        )
    else:
        add("T2", "消费 30", False, "missing token/cust")

    if token:
        r = api("GET", "/api/transactions?page=1&size=15", token=token)
        body = r.get("body") or {}
        add(
            "T3",
            "流水列表",
            r.get("ok") and body.get("success") is not False,
            trunc(r.get("raw")),
        )

        r = api("POST", "/api/auth/wx-login", {"code": "mock-smoke-code"})
        body = r.get("body") or {}
        # endpoint should respond (mock may bindRequired)
        ok = r.get("status") == 200 and body.get("success") is not False
        add("W1", "wx-login mock 接口", ok, f"status={r.get('status')} {trunc(r.get('raw'), 200)}")

    code_checks = [
        ("U2", "login 调用 auth.login", "pages/login/login.vue", "login("),
        ("U3", "home 调用 dashboard", "pages/home/home.vue", "dashboard"),
        ("U4", "customers 列表 API", "pages/customers/customers.vue", "listCustomers"),
        ("U5", "transactions 充值", "pages/transactions/transactions.vue", "recharge"),
        ("U6", "request 401 reLaunch", "utils/request.js", "reLaunch"),
    ]
    for cid, name, path, needle in code_checks:
        ok = file_has(path, needle)
        add(cid, name, ok, f"file=frontend-uniapp/src/{path} needle={needle}")

    passed = sum(1 for x in results if x["pass"])
    total = len(results)
    print(f"\n==== SUMMARY {passed}/{total} PASS ====")

    lines = [
        "# 本地冒烟检查清单 — 登录 / 会员 / 收银",
        "",
        f"- 时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
        f"- 后端: `{BASE}`（已启动 jar / cloud 配置）",
        f"- 账号: `{USER}` / `***`（本机已有店长）",
        "- 前端契约: `frontend-uniapp` → `uni.request` → `/api/**`",
        f"- **结果: {passed} / {total} PASS**",
        "",
        "## API 与契约结果",
        "",
        "| ID | 用例 | 结果 | 详情 |",
        "|----|------|------|------|",
    ]
    for x in results:
        mark = "PASS" if x["pass"] else "FAIL"
        detail = x["detail"].replace("|", "\\|").replace("\n", " ")
        if len(detail) > 160:
            detail = detail[:160] + "…"
        lines.append(f"| {x['id']} | {x['name']} | {mark} | {detail} |")

    lines += [
        "",
        "## 手工 UI 清单（微信开发者工具 / H5）",
        "",
        "| # | 步骤 | 期望 |",
        "|---|------|------|",
        "| 1 | `cd frontend-uniapp && npm run dev:mp-weixin`，导入 `dist/dev/mp-weixin`，勾选不校验合法域名 | 编译成功，进登录页 |",
        f"| 2 | 账号密码登录（`{USER}`） | 进入首页，看到会员/余额/今日数据 |",
        "| 3 | 故意输错密码 | Toast 错误，不进入系统 |",
        "| 4 | 会员 Tab → 搜索/新建会员 | 列表刷新出现新会员 |",
        "| 5 | 进入会员编辑保存 | 名称更新成功 |",
        "| 6 | 收银 → 充值 100 | 成功提示，流水有充值 |",
        "| 7 | 收银 → 消费（员工+服务+4位校验码） | 成功，余额减少 |",
        "| 8 | 清 storage / 等 401 | 自动回登录页 |",
        "| 9 | H5: `npm run dev:h5`，走 Vite proxy `/api` | 同源无 CORS 报错 |",
        "",
        "## 复跑命令",
        "",
        "```powershell",
        "# 后端（若未启动）",
        "java -jar target/ddmo-1.0.0.jar --spring.profiles.active=cloud",
        "# 冒烟",
        "python scripts/smoke_login_customer_tx.py",
        "```",
        "",
        "## 说明",
        "",
        "- 本轮以 **HTTP 契约 + 关键路径代码静态核对** 为主；浏览器/小程序 UI 点击需本地工具完成。",
        "- 消费依赖会员 `verifyCode`（4 位）；店长列表接口通常返回明文校验码。",
        "- 微信一键登录依赖 `app.wx.miniapp.mock=true` 与小程序端 `uni.login`。",
        "",
    ]

    out = ROOT / "docs" / "本地冒烟-登录会员收银.md"
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("wrote", out)

    fails = [x for x in results if not x["pass"]]
    if fails:
        print("FAILURES:")
        for x in fails:
            print(" -", x["id"], x["name"], x["detail"][:220])
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

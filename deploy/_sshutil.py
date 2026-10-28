# -*- coding: utf-8 -*-
"""星语部署脚本的公共 SSH 工具。

凭据一律从环境变量读取，**不再硬编码在脚本里**：

  SW_HOST                 服务器地址（必填）
  SW_PORT                 SSH 端口，默认 22
  SW_USER                 SSH 用户，默认 deploy（不要再直接用 root）
  SW_SSH_KEY              私钥路径，默认 ~/.ssh/id_ed25519
  SW_PASSWORD             密码。仅在密钥不可用时设置，推荐留空
  SW_ACCEPT_NEW_HOST_KEY  设为 1 时接受未知主机指纹（仅首次使用，会打印警告）

推荐做法：把上面的变量写进一个不入库的 .env 文件，用
    `set -a; source deploy/.env; set +a`（bash）
或 PowerShell 里 `Get-Content deploy/.env | ForEach-Object { ... }` 载入。

首次部署建议先手工 `ssh-copy-id` 把公钥装上，之后所有脚本零密码运行。
"""

import os
import sys
from pathlib import Path

try:
    import paramiko
except ImportError:  # pragma: no cover - 运行环境没装依赖时给出明确提示
    sys.exit("缺少 paramiko：请先执行  pip install paramiko")


class ConfigError(RuntimeError):
    pass


def _env(name, default=None, required=False):
    value = os.environ.get(name, default)
    if required and not (value or "").strip():
        raise ConfigError(
            "缺少环境变量 %s。请在 deploy/.env 里配置（参考 deploy/.env.example），"
            "不要在脚本里写死服务器密码。" % name
        )
    return value


def settings():
    """解析并校验连接参数，返回 dict。"""
    host = _env("SW_HOST", required=True).strip()
    port = int(_env("SW_PORT", "22"))
    user = (_env("SW_USER", "deploy") or "deploy").strip()
    key_path = Path(os.path.expanduser(_env("SW_SSH_KEY", "~/.ssh/id_ed25519")))
    password = _env("SW_PASSWORD") or None
    accept_new = (_env("SW_ACCEPT_NEW_HOST_KEY", "0") or "0").strip() == "1"
    return {
        "host": host,
        "port": port,
        "user": user,
        "key_path": key_path,
        "password": password,
        "accept_new": accept_new,
    }


def connect():
    """建立 SSH 连接。优先密钥，其次密码；主机指纹默认必须已存在于 known_hosts。"""
    cfg = settings()
    client = paramiko.SSHClient()

    if cfg["accept_new"]:
        # 首次连接用：接受未知指纹，但明确告警，避免长期开着这个口子
        print(
            "[警告] SW_ACCEPT_NEW_HOST_KEY=1：正在接受未知主机指纹。"
            "首次连接完成后请把它改回 0，并核对 known_hosts 里的指纹。",
            file=sys.stderr,
        )
        client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    else:
        client.set_missing_host_key_policy(paramiko.RejectPolicy())
        client.load_system_host_keys()

    kwargs = {
        "hostname": cfg["host"],
        "port": cfg["port"],
        "username": cfg["user"],
        "timeout": 20,
        "allow_agent": True,
        "look_for_keys": True,
    }
    if cfg["key_path"].is_file():
        kwargs["key_filename"] = str(cfg["key_path"])
    if cfg["password"]:
        kwargs["password"] = cfg["password"]
        kwargs["look_for_keys"] = False

    try:
        client.connect(**kwargs)
    except paramiko.BadHostKeyException as exc:
        client.close()
        raise SystemExit(
            "主机指纹不匹配，连接已中止（可能被中间人劫持）：%s\n"
            "确认无误后，手工从 known_hosts 删除旧记录再连。" % exc
        )
    except paramiko.SSHException as exc:
        client.close()
        raise SystemExit(
            "SSH 连接失败：%s\n"
            "自查：1) SW_HOST/SW_USER 是否正确 2) 公钥是否已装到服务器 "
            "3) 首次连接可临时设 SW_ACCEPT_NEW_HOST_KEY=1" % exc
        )
    return client


def main(argv=None):
    argv = list(sys.argv[1:] if argv is None else argv)
    usage = __doc__.split("用法：")[-1]

    if len(argv) < 2:
        print(usage)
        return 2

    cmd = argv[0]
    client = connect()
    try:
        if cmd == "exec" and len(argv) >= 2:
            _, out, err = client.exec_command(argv[1], timeout=280)
            stdout = out.read().decode("utf-8", "replace")
            stderr = err.read().decode("utf-8", "replace")
            code = out.channel.recv_exit_status()
            if stdout:
                print(stdout, end="")
            if stderr:
                print(stderr, end="", file=sys.stderr)
            return code
        if cmd == "put" and len(argv) >= 3:
            sftp = client.open_sftp()
            try:
                sftp.put(argv[1], argv[2])
            finally:
                sftp.close()
            print("uploaded %s -> %s" % (argv[1], argv[2]))
            return 0
        if cmd == "get" and len(argv) >= 3:
            sftp = client.open_sftp()
            try:
                sftp.get(argv[1], argv[2])
            finally:
                sftp.close()
            print("downloaded %s -> %s" % (argv[1], argv[2]))
            return 0
        print(usage)
        return 2
    finally:
        client.close()


if __name__ == "__main__":
    sys.exit(main())

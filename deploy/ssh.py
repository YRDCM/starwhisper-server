# -*- coding: utf-8 -*-
"""星语项目服务器 SSH 助手。

凭据不再写在本文件里——全部从环境变量读取，说明见 deploy/_sshutil.py。
配置模板：deploy/.env.example（复制成 deploy/.env 后填入，`.env` 不入库）

用法:
  python deploy/ssh.py exec "远程命令"           执行命令，打印 stdout/stderr，以远程退出码退出
  python deploy/ssh.py put <本地文件> <远程路径>  上传文件
  python deploy/ssh.py get <远程文件> <本地路径>  下载文件
"""

import sys

from _sshutil import main

if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

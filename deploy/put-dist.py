# -*- coding: utf-8 -*-
"""上传构建产物到服务器（ssh.py put 的便捷封装）。

凭据从环境变量读取（见 deploy/_sshutil.py），不要在这里写死密码。

用法:
  python deploy/put-dist.py <本地文件> <远程路径>
"""

import sys

from _sshutil import main

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(2)
    sys.exit(main(["put", sys.argv[1], sys.argv[2]]))

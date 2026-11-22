#!/usr/bin/env python3
"""把仓库中的 v1.zip（base64 文本）还原为真正的 v1.zip 二进制文件。

用法：
    python3 decode_v1.py            # 读取同目录 v1.zip，输出 v1_real.zip
    python3 decode_v1.py -o out.zip # 指定输出路径
"""
import argparse, base64, pathlib, sys

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("-i", "--input", default="v1.zip")
    ap.add_argument("-o", "--output", default="v1_real.zip")
    a = ap.parse_args()
    src = pathlib.Path(a.input)
    raw = src.read_text(encoding="utf-8").strip()
    # 去掉可能的 Markdown 代码围栏
    if raw.startswith("```"):
        raw = "\n".join(l for l in raw.splitlines() if not l.strip().startswith("```")).strip()
    try:
        data = base64.b64decode(raw, validate=True)
    except Exception as e:
        print("base64 解码失败:", e, file=sys.stderr)
        sys.exit(1)
    if data[:2] != b"PK":
        print("警告: 解码结果不是 ZIP（缺少 PK 文件头）", file=sys.stderr)
    pathlib.Path(a.output).write_bytes(data)
    print(f"已还原 {a.output} ({len(data)} 字节)")

if __name__ == "__main__":
    main()

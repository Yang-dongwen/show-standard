"""Create MySQL database `show` if missing (name is reserved word)."""
import sys

try:
    import pymysql
except ImportError:
    import subprocess

    subprocess.check_call([sys.executable, "-m", "pip", "install", "pymysql", "-q"])
    import pymysql

HOST = "127.0.0.1"
PORT = 3306
USER = "root"
PASSWORD = "123456"
DB = "show"


def main():
    conn = pymysql.connect(host=HOST, port=PORT, user=USER, password=PASSWORD)
    try:
        with conn.cursor() as cur:
            cur.execute(
                "CREATE DATABASE IF NOT EXISTS `show` "
                "DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
            )
            cur.execute("SHOW DATABASES LIKE %s", (DB,))
            rows = cur.fetchall()
            print("databases:", rows)
        conn.commit()
        print("OK: database `show` is ready")
    finally:
        conn.close()


if __name__ == "__main__":
    main()

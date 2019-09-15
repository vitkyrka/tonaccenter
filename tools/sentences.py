#!/usr/bin/env python3

import sqlite3
import subprocess
import argparse
import json
import csv
import os

def typeit(f):
    try:
        return int(f)
    except:
        return f


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--db', default='sentences.db')
    parser.add_argument('path')
    args = parser.parse_args()

    try:
        os.remove(args.db)
    except:
        pass

    conn = sqlite3.connect(args.db)

    conn.execute('''
    CREATE TABLE sentences (questionFilename TEXT UNIQUE,
                            fullFileName,
                            correct TEXT,
                            wrong TEXT,
                            clipPos INT,
                            question TEXT,
                            full TEXT,
                            level INT)
    ''')

    conn.execute('''
    CREATE TABLE pitch (sentence INTEGER,
                        timestamp REAL,
                        frequency REAL)
    ''')

    conn.execute('''
    CREATE TABLE intensity (sentence INTEGER,
                            timestamp REAL,
                            amplitude REAL)
    ''')

    with open(os.path.join(args.path, 'res', 'meningar.txt'), encoding='latin-1') as csvfile:
        reader = csv.reader(csvfile)
        lines = []
        for row in reader:
            if row[3] == "0":
                row = row[0:3] + [""] + row[3:]

            cursor = conn.cursor()
            qfname = row[2]

            # if 'cough' in qfname:
            #     continue

            try:
                cursor.execute('INSERT INTO sentences VALUES (?, ?, ?, ?, ?, ?, ?, ?)',
                        (row[2],
                         row[3],
                         row[8],
                         row[9],
                         int(row[10]),
                         row[11],
                         row[12],
                         row[7]))
            except sqlite3.IntegrityError:
                continue

            sentence = cursor.lastrowid

            qfname = qfname.replace("_cough", "")

            jsonfn = os.path.join(args.path, 'res', 'audio', f'{qfname}.json')
            with open(jsonfn) as f:
                data = json.load(f)

            conn.executemany('INSERT INTO pitch VALUES (?, ?, ?)',
                    ((sentence, ts, freq) for ts, freq in data['pitch']))

            conn.executemany('INSERT INTO intensity VALUES (?, ?, ?)',
                    ((sentence, ts, amplitude) for ts, amplitude in data['intensity']))

        conn.commit()
        conn.close()
        print(subprocess.check_output(['sqlite3', args.db, '.dump']).decode('utf-8'))

if __name__ == '__main__':
    main()

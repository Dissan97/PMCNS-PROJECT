#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Estrae T_total ordinati per t_complete e li salva in 'autocorrelation_data.txt'
"""

# Commenti in italiano come richiesto
import sys
import pandas as pd
import numpy as np

def main(in_csv: str, out_txt: str = "autocorrelation_data.txt"):
    # 1) Carica il CSV
    df = pd.read_csv(in_csv)

    # 2) Controlli minimi sulle colonne richieste
    required = {"t_complete", "T_total"}
    missing = required - set(df.columns)
    if missing:
        raise SystemExit(f"Errore: mancano le colonne {missing} nel CSV.")

    # 3) Coerzione numerica + rimozione NaN
    df["t_complete"] = pd.to_numeric(df["t_complete"], errors="coerce")
    df["T_total"]    = pd.to_numeric(df["T_total"],    errors="coerce")
    df = df.dropna(subset=["t_complete", "T_total"])

    # 4) Ordina per tempo di completamento crescente
    df = df.sort_values("t_complete", kind="mergesort")  # stabile

    # 5) Estrai la serie e salva in txt (1 valore per riga, senza header)
    s = df["T_total"].to_numpy(dtype=float)
    np.savetxt(out_txt, s, fmt="%.10f")

    print(f"OK: scritti {s.size} valori in '{out_txt}' (ordinati per t_complete).")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Uso: python make_autocorr_txt.py <input.csv> [output.txt]")
        sys.exit(1)
    in_csv = sys.argv[1]
    out_txt = sys.argv[2] if len(sys.argv) >= 3 else "autocorrelation_data.txt"
    main(in_csv, out_txt)

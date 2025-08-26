#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Generatore di seed per substream Lehmer (Park–Miller) con:
  - modulo m = 2^31 - 1
  - moltiplicatore a = 48271

Ogni seed restituito rappresenta l'inizio di un substream separato dal precedente
di L estrazioni, così da evitare sovrapposizioni tra run.

Uso come libreria:
    from generate_lehmer_seeds import generate_seeds
    seeds = generate_seeds(n=10, s0=271_828_183)  # lista di 10 seed

Uso da CLI:
    python generate_lehmer_seeds.py --n 10 --seed 271828183 --L 6000000 --out seeds.csv
    # Se --out non è indicato, stampa i seed su stdout (uno per riga).

Nota: in Python l'operazione pow(a, L, m) è efficiente e usa esponenziazione modulare.
"""

from __future__ import annotations
import argparse
import sys
from typing import List

# Costanti del generatore Lehmer (Park–Miller) "migliorato"
M: int = 2_147_483_647   # modulo primo (2^31 - 1)
A: int = 48_271          # moltiplicatore consigliato (periodo pieno, buone proprietà spettrali)

def _jump_multiplier(L: int, a: int = A, m: int = M) -> int:
    """Calcola A_L = a^L mod m, ovvero il moltiplicatore di salto di L passi.
    Questo consente di ottenere seed di substream separati da L estrazioni.
    """
    if L <= 0:
        raise ValueError("L deve essere positivo.")
    return pow(a, L, m)

def generate_seeds(n: int, s0: int, L: int = 6_000_000, a: int = A, m: int = M) -> List[int]:
    """Genera n seed per substream Lehmer non sovrapposti.
    
    Parametri:
      - n: numero di seed da generare (>= 1)
      - s0: seed iniziale (1 <= s0 < m)
      - L: numero di estrazioni consumate per run (salto tra substream), default 6_000_000
      - a: moltiplicatore Lehmer (default 48271)
      - m: modulo primo (default 2^31 - 1)
    
    Ritorna:
      - Lista di n interi, dove il k-esimo è il seed del substream k.
    
    Note:
      - s0 non deve essere 0 (stato assorbente). Qualsiasi 1 <= s0 <= m-1 è valido.
      - Se cambi L (perché la run consuma più/meno numeri), i seed restano indipendenti
        a patto di mantenere lo stesso schema di salto A_L = a^L mod m.
    """
    if n <= 0:
        raise ValueError("n deve essere >= 1.")
    if not (1 <= s0 < m):
        raise ValueError(f"s0 deve essere in [1, {m-1}].")
    if L <= 0:
        raise ValueError("L deve essere positivo.")
    # Calcolo una sola volta il moltiplicatore di salto
    A_L = _jump_multiplier(L, a=a, m=m)
    seeds = [s0]
    for _ in range(n - 1):
        seeds.append((A_L * seeds[-1]) % m)
    return seeds

def _cli() -> None:
    """Interfaccia a riga di comando: genera seed e li stampa o salva su file CSV."""
    parser = argparse.ArgumentParser(description="Genera seed per substream Lehmer (m=2^31-1, a=48271).")
    parser.add_argument("--n", type=int, required=True, help="Numero di seed da generare (>=1).")
    parser.add_argument("--seed", type=int, required=True, help="Seed iniziale s0 (1 <= s0 < m).")
    parser.add_argument("--L", type=int, default=6_000_000, help="Salto tra substream (estrazioni per run). Default: 6_000_000.")
    parser.add_argument("--out", type=str, default="", help="Percorso file CSV di output; se omesso, stampa su stdout.")
    args = parser.parse_args()

    try:
        seeds = generate_seeds(n=args.n, s0=args.seed, L=args.L)
    except Exception as e:
        print(f"Errore: {e}", file=sys.stderr)
        sys.exit(1)

    if args.out:
        # Scrive un CSV con singola colonna 'seed'
        try:
            with open(args.out, "w", encoding="utf-8") as f:
                f.write("seed\n")
                for s in seeds:
                    f.write(f"{s}\n")
        except Exception as e:
            print(f"Errore nello scrivere il file '{args.out}': {e}", file=sys.stderr)
            sys.exit(2)
    else:
        # Stampa i seed, uno per riga
        for s in seeds:
            print(s)

if __name__ == "__main__":
    _cli()

# # -------------------------------------------------------------------------
#  * This program is based on a one-pass algorithm for the calculation of an
#  * array of autocorrelations r[1], r[2], ... r[K].  The key feature of this
#  * algorithm is the circular array 'hold' which stores the (K + 1) most
#  * recent data points and the associated index 'p' which points to the
#  * (rotating) head of the array.
#  *
#  * Data is read from a text file in the format 1-data-point-per-line (with
#  * no blank lines).  Similar to programs UVS and BVS, this program is
#  * designed to be used with OS redirection.
#  *
#  * NOTE: the constant K (maximum lag) MUST be smaller than the # of data
#  * points in the text file, n.  Moreover, if the autocorrelations are to be
#  * statistically meaningful, K should be MUCH smaller than n.
#  *
#  * Name              : acs.c  (AutoCorrelation Statistics)
#  * Author            : Steve Park & Dave Geyer
#  * Language          : ANSI C
#  * Latest Revision   : 2-10-97
#  * Compile with      : gcc -lm acs.c
#  * Execute with      : acs.out < acs.dat
#  # Translated by     : Philip Steele
#  # Language          : Python 3.3
#  # Latest Revision   : 3/26/14
#  * Execute with      : python acs.py < acs.dat
#  * -------------------------------------------------------------------------
#  */

#include <stdio.h>
#include <math.h>

import sys
from math import sqrt

''' 
    Leggermente modificato per stampare più chiaro i risultati, 
    ma la logica rimane la stessa del autore originale 
'''


# --- PARAMETRI ---
K = 6000  # Massimo lag da calcolare (aumentato per un'analisi più approfondita)
FILENAME = "autocorrelation_data.txt"  # Nome del file da leggere

# --- Inizializzazione ---
SIZE = K + 1
i = 0
sum_val = 0.0
hold = []
p = 0
cosum = [0.0] * SIZE  # Inizializzato a float

try:
    with open(FILENAME, "r") as file:
        # Inizializza l'array 'hold'
        while i < SIZE:
            line = file.readline()
            if not line: raise EOFError("File terminato prima di inizializzare l'array 'hold'.")
            x = float(line)
            sum_val += x
            hold.append(x)
            i += 1

        # Processa il resto del file
        for line in file:
            for j in range(SIZE):
                cosum[j] += hold[p] * hold[(p + j) % SIZE]

            x = float(line)
            sum_val += x
            hold[p] = x
            p = (p + 1) % SIZE
            i += 1

        n = i  # Numero totale di punti

        # Svuota l'array circolare alla fine
        for _ in range(SIZE):
            for j in range(SIZE):
                cosum[j] += hold[p] * hold[(p + j) % SIZE]
            hold[p] = 0.0
            p = (p + 1) % SIZE

        # Calcola le statistiche finali
        mean = sum_val / n
        for j in range(K + 1):
            if n - j > 0:
                cosum[j] = (cosum[j] / (n - j)) - (mean * mean)

        print(f"Per {n} punti dati:")
        print(f"La media è ... {mean:8.4f}")
        if cosum[0] > 0:
            print(f"La dev. std è .. {sqrt(cosum[0]):8.4f}\n")
            print("  j (lag)   r[j] (autocorrelazione)")
            print("-" * 35)
            for j in range(1, SIZE):
                r_j = cosum[j] / cosum[0]
                print(f"{j:5d}       {r_j:8.4f}")
                if abs(r_j) < 0.2:
                    print(f"\n--- Autocorrelazione scesa sotto 0.2 al lag {j} ---")
                    print(f"--- BATCH_SIZE suggerito: almeno {j * 5} o {j * 10} ---")
                    break
        else:
            print("Varianza non positiva, impossibile calcolare l'autocorrelazione.")

except FileNotFoundError:
    print(f"Errore: file '{FILENAME}' non trovato.")
except (ValueError, EOFError) as e:
    print(f"Errore durante la lettura del file: {e}")
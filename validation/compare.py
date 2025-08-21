# validation/compare.py
import numpy as np
import pandas as pd
from scipy import stats

def ci_mean(data, alpha=0.05):
    n = len(data)
    if n < 2:
        return data[0], 0.0
    m  = np.mean(data)
    se = stats.sem(data)
    h  = se * stats.t.ppf(1 - alpha/2, n - 1)
    return m, h

def diff_pct(a, b):
    return abs(a - b) / a * 100 if a else np.nan

def compare(analytic: dict, sim_reps: list[dict]):
    """
    analytic   : dict con le metriche teoriche
    sim_reps   : list[dict] con le stesse metriche via simulazione
    Restituisce {metric: {analytic, sim_mean, ci, diff_pct}}
    """
    df  = pd.DataFrame(sim_reps)

    # considera solo le colonne numeriche (salta 'scope')
    num_cols = df.select_dtypes(include=[np.number]).columns

    res = {}
    for col in num_cols:
        m, h = ci_mean(df[col].values)
        tgt  = analytic.get(col)
        res[col] = dict(
            analytic = tgt,
            sim_mean = m,
            ci       = h,
            diff_pct = diff_pct(tgt, m)
        )
    return res

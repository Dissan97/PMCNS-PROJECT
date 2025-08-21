import pandas as pd
from pathlib import Path

def load_sim_csv(path: Path):
    df = pd.read_csv(path)
    overall = df[df.scope=="OVERALL"].squeeze().to_dict()
    per_node = {row.scope.split("_")[1]: row[1:].to_dict()
                for _, row in df[df.scope.str.startswith("NODE_")].iterrows()}
    return overall, per_node

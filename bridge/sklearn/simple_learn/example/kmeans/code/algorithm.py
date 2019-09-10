import pickle

import numpy as np
from sklearn.cluster import KMeans

import mantik
import mantik.types


MODEL_FILE = "model.pickle"


def train(bundle: mantik.types.Bundle) -> mantik.types.Bundle:
    coordinates = bundle.flat_column("coordinates")
    learn_data = np.array(coordinates)
    cluster_count = 2  # TODO Should be meta variable.
    model = KMeans(n_clusters=cluster_count).fit(learn_data)
    with (open(MODEL_FILE, "wb")) as f:
        pickle.dump(model, f)
    value = [[model.cluster_centers_.reshape(-1).tolist(), model.inertia_, model.n_iter_]]
    return mantik.types.Bundle(value=value)


def try_init():
    with (open(MODEL_FILE, "rb")) as f:
        return pickle.load(f)


def apply(model, bundle: mantik.types.Bundle) -> mantik.types.Bundle:
    coordinates = bundle.flat_column("coordinates")
    data = np.array(coordinates)
    result = model.predict(data)
    return mantik.types.Bundle.from_flat_column(result.tolist())

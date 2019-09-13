from __future__ import annotations

import contextlib
import dataclasses
import functools
import logging
from typing import List, Optional, Union, Any

from google.protobuf.empty_pb2 import Empty

import mantik.types
from mantik.engine.compat import *

logger = logging.getLogger(__name__)


@functools.singledispatch
def _convert(bundle: mantik.types.Bundle) -> Bundle:
    """Convert mantik.types.Bundle to its protobuf equivalent."""
    return Bundle(
        data_type=DataType(json=bundle.type.to_json()),
        encoding=ENCODING_JSON,
        encoded=bundle.encode_json(),
    )


@_convert.register
def _(bundle: Bundle) -> mantik.types.Bundle:
    return mantik.types.Bundle.decode_json(
        bundle.encoded,
        assumed_type=mantik.types.DataType.from_json(bundle.data_type.json),
    )


@dataclasses.dataclass
class MantikItem:
    item: Any
    _session: Any
    _graph_executor: Any

    @property
    def item_id(self):
        return self.item.item_id

    def __str__(self):
        return str(self.item)

    def fetch(self) -> MantikItem:
        if not hasattr(self, "_f_response"):
            self._f_response: FetchItemResponse = self._graph_executor.FetchDataSet(
                FetchItemRequest(
                    session_id=self._session.session_id,
                    dataset_id=self.item_id,
                    encoding=ENCODING_JSON,
                )
            )
            self.bundle = _convert(self._f_response.bundle)
        return self

    def save(self, name=None) -> MantikItem:
        if not hasattr(self, "_s_response"):
            save_args = dict(session_id=self._session.session_id, item_id=self.item_id)
            if name is not None:
                save_args["name"] = name
            self._s_reponse: SaveItemResponse = self._graph_executor.SaveItem(
                SaveItemRequest(**save_args)
            )
        return self

    def deploy(self, ingress_name=str) -> MantikItem:
        if not hasattr(self, "_d_response"):
            self._d_response: DeployItemResponse = self._graph_executor.DeployItem(
                DeployItemRequest(
                    session_id=self._session.session_id,
                    item_id=self.item_id,
                    ingress_name=ingress_name,
                )
            )
        return self


SomeData = Union[DataType, mantik.types.DataType]
PipeStep = Union[str, MantikItem, NodeResponse]


class Client(object):
    def __init__(self, host, port):
        self.host = host
        self.port = port
        self._session = None

    # TODO (mq): use LocalProxy's everywhere
    @property
    def session(self):
        if self._session is None:
            raise RuntimeError("Outside of a session scope.")
        return self._session

    @property
    def version(self):
        response: VersionResponse = self._about_service.Version(Empty())
        return response.version

    def _make_item(self, result):
        """Return a fetchable dataset"""
        return MantikItem(result, self._session, self._graph_executor)

    def _add_algorithm(self, directory: str) -> MantikItem:
        response = self._debug_service.AddLocalMantikDirectory(
            AddLocalMantikDirectoryRequest(directory=directory)
        )
        logger.debug("Added item %s", response.name)
        return self._make_item(response)

    def upload_bundle(self, bundle: mantik.types.Bundle) -> MantikItem:
        """Upload the bundle and create a mantik data literal."""
        # TODO (mq): cache this
        item = self._make_item(
            self._graph_builder.Literal(
                LiteralRequest(
                    session_id=self.session.session_id, bundle=_convert(bundle)
                )
            )
        )
        logger.debug("Created Literal Node %s", item.item_id)
        return item

    def make_pipeline(
        self, steps: List[PipeStep], data: Optional[SomeData] = None
    ) -> MantikItem:
        """Translate a list of references to a mantik Pipeline.

        A reference is either the name of an algorithm or a select literal.
        If the first pipeline step is a select literal, the input datatype must be supplied via a bundle.

        """

        def guess_input_type(data):
            if isinstance(data, mantik.types.Bundle):
                return DataType(json=data.type.to_json())
            # it should be a Literal
            return data.item.item.dataset.type

        def build_step(s):
            if isinstance(s, str):
                if s.startswith("select "):  # is a select literal
                    return BuildPipelineStep(select=s)
                algorithm = self._graph_builder.Get(
                    GetRequest(session_id=self.session.session_id, name=s)
                )
            else:
                algorithm = s
            return BuildPipelineStep(algorithm_id=algorithm.item_id)

        pipe_steps = map(build_step, steps)
        request_args = dict(session_id=self.session.session_id, steps=pipe_steps)

        # if pipe starts with a select, need to supply input datatype
        first = steps[0]
        if (isinstance(first, str) and first.startswith("select ")) or isinstance(
            first, SelectRequest
        ):
            request_args["input_type"] = guess_input_type(data)

        # TODO (mq): catch and convert exceptions to be pythonic
        pipe = self._graph_builder.BuildPipeline(BuildPipelineRequest(**request_args))
        return self._make_item(pipe)

    def apply(
        self, pipe: Union[PipeStep, List[PipeStep]], data: SomeData
    ) -> MantikItem:
        """Execute the pipeline pipe on some data."""
        dataset = (
            self.upload_bundle(data) if isinstance(data, mantik.types.Bundle) else data
        )
        mantik_pipe = (
            pipe
            if isinstance(pipe, (MantikItem, NodeResponse))
            else self.make_pipeline(pipe, data)
        )
        result = self._graph_builder.AlgorithmApply(
            ApplyRequest(
                session_id=self.session.session_id,
                algorithm_id=mantik_pipe.item_id,
                dataset_id=dataset.item_id,
            )
        )
        return self._make_item(result)

    def train(self, pipe, data, caching=True):
        """Transform a trainable pipeline to a pipeline.

        Only the last element must be a TrainableAlgorithm.


        Returns:
            - The trained algorithm
            - Statistics about the training
        """
        dataset = (
            self.upload_bundle(data) if isinstance(data, mantik.types.Bundle) else data
        )
        features = self.apply(pipe[:-1], dataset) if len(pipe) > 1 else dataset
        last = pipe[-1]
        name = last if isinstance(last, str) else last.item.name
        trainable = self._graph_builder.Get(
            GetRequest(session_id=self.session.session_id, name=name)
        )
        train_response = self._graph_builder.Train(
            TrainRequest(
                session_id=self.session.session_id,
                trainable_id=trainable.item_id,
                training_dataset_id=features.item_id,
                no_caching=not caching,
            )
        )
        trained_algorithm = self._make_item(train_response.trained_algorithm)
        stats = self._make_item(train_response.stat_dataset)
        return self.make_pipeline([*pipe[:-1], trained_algorithm], data), stats.fetch()

    def tag(self, algo: MantikItem, ref: str) -> MantikItem:
        """Tag an algorithm algo with a reference ref."""
        return self._make_item(
            self._graph_builder.Tag(
                TagRequest(
                    session_id=self.session.session_id,
                    item_id=algo.item_id,
                    named_mantik_id=ref,
                )
            )
        ).save(name=ref)

    @contextlib.contextmanager
    def enter_session(self):
        if self._session is not None:
            raise RuntimeError("Cannot stack sessions.")
        self._session = self._session_service.CreateSession(CreateSessionRequest())
        logger.debug("Created session %s", self.session.session_id)
        yield self
        self._session_service.CloseSession(
            CloseSessionRequest(session_id=self.session.session_id)
        )
        logger.debug("Closed session %s", self.session.session_id)

        self._session = None

    def __enter__(self):
        channel = grpc.insecure_channel(f"{self.host}:{self.port}")
        self._about_service = AboutServiceStub(channel)
        self._session_service = SessionServiceStub(channel)
        self._debug_service = DebugServiceStub(channel)
        self._graph_builder = GraphBuilderServiceStub(channel)
        self._graph_executor = GraphExecutorServiceStub(channel)

        self._connected = True
        logger.info("Connected to version %s", self.version)

        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self._connected = False

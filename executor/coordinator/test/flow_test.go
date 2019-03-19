package test

import (
	"coordinator/service/coordinator"
	"coordinator/service/protocol"
	"coordinator/service/sidecar"
	"coordinator/testutil"
	"fmt"
	"github.com/stretchr/testify/assert"
	"sync"
	"testing"
	"time"
)

var randomPortSettings = protocol.CreateRandomPortSettings()

func TestSimpleABCopy(t *testing.T) {
	testData := []byte("Hello World")

	ts1 := testutil.CreateSampleSource("Source", testData)
	defer ts1.Close()

	ts2 := testutil.CreateSampleSink("Sink")
	defer ts2.Close()

	sideCar1, err := sidecar.CreateSideCar(randomPortSettings, ts1.URL, false)
	assert.NoError(t, err)

	sideCar2, err := sidecar.CreateSideCar(randomPortSettings, ts2.URL, false)
	assert.NoError(t, err)

	plan := coordinator.Plan{
		Nodes: map[string]coordinator.Node{
			"A": {fmt.Sprintf("localhost:%d", sideCar1.Port())},
			"B": {fmt.Sprintf("localhost:%d", sideCar2.Port())},
		},
		Flows: []coordinator.Flow{
			coordinator.Flow{
				coordinator.NodeResourceRef{"A", "Source"},
				coordinator.NodeResourceRef{"B", "Sink"},
			},
		},
	}

	c, err := coordinator.CreateCoordinator("localhost", randomPortSettings, &plan)
	assert.NoError(t, err)

	var group sync.WaitGroup
	// SideCars are stopped by the coordinator
	group.Add(2)
	go func() {
		defer group.Done()
		sideCar1.Run()
	}()
	go func() {
		defer group.Done()
		sideCar2.Run()
	}()
	err = c.Run()
	group.Wait()
	assert.NoError(t, err)
	assert.Equal(t, 1, ts1.Requests)
	assert.Equal(t, 1, ts2.Requests)
	assert.Equal(t, testData, ts2.RequestData[0])
	assert.False(t, ts1.QuitRequested)
	assert.False(t, ts2.QuitRequested)
}

func TestQuitRequested(t *testing.T) {
	testData := []byte("Hello World")

	ts1 := testutil.CreateSampleSource("Source", testData)
	defer ts1.Close()

	ts2 := testutil.CreateSampleSink("Sink")
	defer ts2.Close()

	sideCar1, err := sidecar.CreateSideCar(randomPortSettings, ts1.URL, true)
	assert.NoError(t, err)

	sideCar2, err := sidecar.CreateSideCar(randomPortSettings, ts2.URL, true)
	assert.NoError(t, err)

	plan := coordinator.Plan{
		Nodes: map[string]coordinator.Node{
			"A": {fmt.Sprintf("localhost:%d", sideCar1.Port())},
			"B": {fmt.Sprintf("localhost:%d", sideCar2.Port())},
		},
		Flows: []coordinator.Flow{
			coordinator.Flow{
				coordinator.NodeResourceRef{"A", "Source"},
				coordinator.NodeResourceRef{"B", "Sink"},
			},
		},
	}

	c, err := coordinator.CreateCoordinator("localhost", randomPortSettings, &plan)
	assert.NoError(t, err)

	var group sync.WaitGroup
	// SideCars are stopped by the coordinator
	group.Add(2)
	go func() {
		defer group.Done()
		sideCar1.Run()
	}()
	go func() {
		defer group.Done()
		sideCar2.Run()
	}()
	err = c.Run()
	group.Wait()
	assert.NoError(t, err)
	assert.Equal(t, 1, ts1.Requests)
	assert.Equal(t, 1, ts2.Requests)
	assert.Equal(t, testData, ts2.RequestData[0])
	assert.True(t, ts1.QuitRequested)
	assert.True(t, ts2.QuitRequested)
}

func TestSimpleABCFlow(t *testing.T) {
	testData := []byte("Hello World")
	abc := CreateAbcFlowNodes(testData)

	c, err := coordinator.CreateCoordinator("localhost", randomPortSettings, &abc.plan)
	assert.NoError(t, err)

	abc.runSideCars()
	err = c.Run()
	assert.NoError(t, err)
	assert.Equal(t, 1, abc.sourceServer.Requests)
	assert.Equal(t, 1, abc.transformServer.Requests)
	assert.Equal(t, 1, abc.sinkServer.Requests)
	assert.Equal(t, testData, abc.sinkServer.RequestData[0])
	assert.Equal(t, testData, abc.transformServer.RequestData[0])
	abc.waitUntilSideCarEnd()
}

func TestLearnFlow(t *testing.T) {
	testData := []byte("Hello World")

	learnData := testutil.CreateSampleSource("Source", testData)
	defer learnData.Close()

	learnProcess := testutil.CreateLearnLikeServer("In", "State", "Result")
	defer learnProcess.Close()

	stateSink := testutil.CreateSampleSink("StateSink")
	defer stateSink.Close()

	resultSink := testutil.CreateSampleSink("ResultSink")
	defer resultSink.Close()

	randomPortSettings := protocol.CreateRandomPortSettings()

	inCar, err := sidecar.CreateSideCar(randomPortSettings, learnData.URL, false)
	assert.NoError(t, err)

	learnCar, err := sidecar.CreateSideCar(randomPortSettings, learnProcess.URL, false)
	assert.NoError(t, err)

	stateSinkCar, err := sidecar.CreateSideCar(randomPortSettings, stateSink.URL, false)
	assert.NoError(t, err)

	resultSinkCar, err := sidecar.CreateSideCar(randomPortSettings, resultSink.URL, false)
	assert.NoError(t, err)

	plan := coordinator.Plan{
		Nodes: map[string]coordinator.Node{
			"in":     {fmt.Sprintf("localhost:%d", inCar.Port())},
			"learn":  {fmt.Sprintf("localhost:%d", learnCar.Port())},
			"state":  {fmt.Sprintf("localhost:%d", stateSinkCar.Port())},
			"result": {fmt.Sprintf("localhost:%d", resultSinkCar.Port())},
		},
		Flows: []coordinator.Flow{
			coordinator.Flow{
				coordinator.NodeResourceRef{"in", "Source"},
				coordinator.NodeResourceRef{"learn", "In"},
			},
			coordinator.Flow{
				coordinator.NodeResourceRef{"learn", "State"},
				coordinator.NodeResourceRef{"state", "StateSink"},
			},
			coordinator.Flow{
				coordinator.NodeResourceRef{"learn", "Result"},
				coordinator.NodeResourceRef{"result", "ResultSink"},
			},
		},
	}

	c, err := coordinator.CreateCoordinator("localhost", randomPortSettings, &plan)
	assert.NoError(t, err)

	var group sync.WaitGroup
	// SideCars are stopped by the coordinator
	group.Add(4)
	go func() {
		defer group.Done()
		inCar.Run()
	}()
	go func() {
		defer group.Done()
		learnCar.Run()
	}()
	go func() {
		defer group.Done()
		stateSinkCar.Run()
	}()
	go func() {
		defer group.Done()
		resultSinkCar.Run()
	}()
	err = c.Run()
	assert.NoError(t, err)
	assert.Equal(t, 1, learnData.Requests)
	assert.Equal(t, 1, learnProcess.Requests)
	assert.Equal(t, 1, stateSink.Requests)
	assert.Equal(t, 1, resultSink.Requests)

	// See behaviour of sample learning server
	assert.Equal(t, "11", string(stateSink.RequestData[0]))
	assert.Equal(t, "HloWrd", string(resultSink.RequestData[0]))
	group.Wait()
}

func TestCustomMimeType(t *testing.T) {
	testData := []byte("Hello World")
	abc := CreateAbcFlowNodes(testData)
	contentType := "application/x-msgpack"
	abc.plan.ContentType = &contentType

	c, err := coordinator.CreateCoordinator("localhost", randomPortSettings, &abc.plan)
	assert.NoError(t, err)

	abc.runSideCars()
	err = c.Run()
	assert.NoError(t, err)
	assert.Equal(t, 1, abc.sourceServer.Requests)
	assert.Equal(t, 1, abc.transformServer.Requests)
	assert.Equal(t, 1, abc.sinkServer.Requests)
	assert.Equal(t, contentType, abc.sourceServer.MimeTypes[0])
	assert.Equal(t, contentType, abc.transformServer.MimeTypes[0])
	assert.Equal(t, contentType, abc.sinkServer.MimeTypes[0])
	assert.Equal(t, testData, abc.sinkServer.RequestData[0])
	assert.Equal(t, testData, abc.transformServer.RequestData[0])
	abc.waitUntilSideCarEnd()
}

func TestCoordinatorWaitSideCars(t *testing.T) {
	testData := []byte("Hello World")

	sideCar1Port := testutil.GetFreeTcpListeningPort()
	sideCar2Port := testutil.GetFreeTcpListeningPort()

	ts1 := testutil.CreateSampleSource("Source", testData)
	defer ts1.Close()

	ts2 := testutil.CreateSampleSink("Sink")
	defer ts2.Close()

	sideCar1Settings := protocol.CreateDefaultSettings()
	sideCar1Settings.Port = sideCar1Port

	sideCar2Settings := protocol.CreateDefaultSettings()
	sideCar2Settings.Port = sideCar2Port

	plan := coordinator.Plan{
		Nodes: map[string]coordinator.Node{
			"A": {fmt.Sprintf("localhost:%d", sideCar1Port)},
			"B": {fmt.Sprintf("localhost:%d", sideCar2Port)},
		},
		Flows: []coordinator.Flow{
			coordinator.Flow{
				coordinator.NodeResourceRef{"A", "Source"},
				coordinator.NodeResourceRef{"B", "Sink"},
			},
		},
	}

	var group sync.WaitGroup
	// SideCars are stopped by the coordinator
	group.Add(3)
	go func() {
		defer group.Done()
		c, err := coordinator.CreateCoordinator("localhost", randomPortSettings, &plan)
		assert.NoError(t, err)
		err = c.Run()
		assert.NoError(t, err)

	}()
	time.Sleep(100 * time.Millisecond)
	go func() {
		defer group.Done()
		sideCar1, err := sidecar.CreateSideCar(sideCar1Settings, ts1.URL, false)
		assert.NoError(t, err)
		sideCar1.Run()
	}()
	go func() {
		defer group.Done()
		sideCar2, err := sidecar.CreateSideCar(sideCar2Settings, ts2.URL, false)
		assert.NoError(t, err)
		sideCar2.Run()
	}()
	group.Wait()
	assert.Equal(t, 1, ts1.Requests)
	assert.Equal(t, 1, ts2.Requests)
	assert.Equal(t, testData, ts2.RequestData[0])
	group.Wait()
}

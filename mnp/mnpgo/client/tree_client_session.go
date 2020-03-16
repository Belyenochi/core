package client

import (
	"context"
	"github.com/pkg/errors"
	"github.com/sirupsen/logrus"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo/util"
	"golang.org/x/sync/errgroup"
	"io"
)

/* Ready initialized tree client in a session. */
type TreeClientSession struct {
	sessionId   string
	mainInputs  []portNode
	mainOutputs []portNode
	c           *ConnectedTreeClient
}

func (t *TreeClientSession) Quit() error {
	var firstError error
	for _, c := range t.c.connections {
		err := c.QuitSession(context.Background(), t.sessionId)
		if err != nil {
			logrus.Warnf("Error closing a session on %s: %s", c.Address(), err.Error())
			if firstError == nil {
				firstError = err
			}
		}
	}
	return firstError
}

func (t *TreeClientSession) InputCount() int {
	return len(t.mainInputs)
}

func (t *TreeClientSession) OutputCount() int {
	return len(t.mainOutputs)
}

func (t *TreeClientSession) RunTask(
	ctx context.Context,
	taskId string,
	input []io.Reader,
	output []io.WriteCloser,
) error {
	if len(input) != t.InputCount() {
		return errors.New("Input count mismatch")
	}
	if len(output) != t.OutputCount() {
		return errors.New("Output count mismatch")
	}

	eg, childContext := errgroup.WithContext(ctx)

	for id, input := range input {
		newId := id
		newInput := input
		inputInfo := t.mainInputs[newId]
		connection := t.c.connections[inputInfo.node]
		eg.Go(func() error {
			wrapped, err := util.WrapPushToWriter(connection.ServiceClient(), childContext, t.sessionId, taskId, inputInfo.port)
			if err != nil {
				return err
			}
			n, err := io.Copy(wrapped, newInput)
			if err != nil {
				logrus.Warnf("Writing input %d failed in task %s", newId, taskId)
				return err
			}
			logrus.Debugf("Written %d bytes to input channel %d", n, newId)
			err = wrapped.Close()
			if err != nil {
				return err
			}
			return nil
		})
	}

	for id, output := range output {
		newId := id
		newOutput := output
		outputInfo := t.mainOutputs[newId]
		connection := t.c.connections[outputInfo.node]
		eg.Go(func() error {
			wrapped, err := util.WrapPullToReader(connection.ServiceClient(), childContext, t.sessionId, taskId, outputInfo.port)
			if err != nil {
				logrus.Warnf("Reading output %d failed in task %s", newId, taskId)
				return err
			}
			defer newOutput.Close()
			n, err := io.Copy(newOutput, wrapped)
			if err != nil {
				return err
			}
			logrus.Debugf("Written %d bytes to output channel %d", n, newId)
			return nil
		})
	}
	err := eg.Wait()
	if err != nil {
		logrus.Warnf("Task %s failed: %s", taskId, err.Error())
	}
	return err
}

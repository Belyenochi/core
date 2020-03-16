package server

import (
	"bytes"
	"context"
	"github.com/sirupsen/logrus"
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo/client"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo/server/internal"
	"io"
	"testing"
)

func TestTreeCalculation(t *testing.T) {
	logrus.SetLevel(logrus.DebugLevel)
	var dummy1 internal.DummyHandler
	var dummy2 internal.DummyHandler

	s1 := setupServer(t, &dummy1)
	defer tearDown(s1)

	s2 := setupServer(t, &dummy2)
	defer tearDown(s2)

	c := client.NewTreeClient(context.Background())

	err := c.AddNode("n1", s1.Address())
	assert.NoError(t, err)

	err = c.AddNode("n2", s2.Address())
	assert.NoError(t, err)

	connected, err := c.Connect()
	assert.NoError(t, err)

	session, err := connected.PrepareSession("session1")
	assert.NoError(t, err)

	err = session.AddInit("n1", nil, mnpgo.PortConfiguration{
		Inputs:  []mnpgo.InputPortConfiguration{{"abcd"}},
		Outputs: []mnpgo.OutputPortConfiguration{{"out1"}, {"out2"}},
	})
	assert.NoError(t, err)

	session.AddInit("n2", nil, mnpgo.PortConfiguration{
		Inputs:  []mnpgo.InputPortConfiguration{{"foobar"}},
		Outputs: []mnpgo.OutputPortConfiguration{{"out3"}, {"out4"}},
	})
	assert.NoError(t, err)

	err = session.AddForwarding("n1", 1, "n2", 0)
	assert.NoError(t, err)

	in1, err := session.MainInput("n1", 0)
	assert.NoError(t, err)
	assert.Equal(t, 0, in1)

	out1, err := session.MainOutput("n1", 0)
	assert.NoError(t, err)
	assert.Equal(t, 0, out1)

	out2, err := session.MainOutput("n2", 0)
	assert.NoError(t, err)
	assert.Equal(t, 1, out2)

	out3, err := session.MainOutput("n2", 1)
	assert.NoError(t, err)
	assert.Equal(t, 2, out3)

	initialized, err := session.Initialize()
	assert.NoError(t, err)

	assert.Equal(t, 1, initialized.InputCount())
	assert.Equal(t, 3, initialized.OutputCount())

	input := bytes.NewBuffer([]byte{1, 2, 3, 4})
	output1 := CloseableBuffer{}
	output2 := CloseableBuffer{}
	output3 := CloseableBuffer{}

	err = initialized.RunTask(
		context.Background(),
		"task1",
		[]io.Reader{input},
		[]io.WriteCloser{&output1, &output2, &output3},
	)

	assert.NoError(t, err)
	assert.True(t, output1.Closed)
	assert.True(t, output2.Closed)
	assert.True(t, output3.Closed)
	assert.Equal(t, []byte{1, 2, 3, 4}, output1.Buffer.Bytes())
	assert.Equal(t, []byte{2, 4, 6, 8}, output2.Buffer.Bytes())
	assert.Equal(t, []byte{4, 8, 12, 16}, output3.Buffer.Bytes())

	err = initialized.Quit()
	assert.NoError(t, err)

	assert.Equal(t, 1, len(dummy1.Sessions))
	assert.True(t, dummy1.Sessions[0].Quitted)
	assert.Equal(t, 1, len(dummy2.Sessions))
	assert.True(t, dummy2.Sessions[0].Quitted)
}

package runner

import (
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"io"
)

/** A Runner for multi table generators */
type MultiGeneratorRunner interface {
	Run(input []element.StreamReader, output []element.StreamWriter) error
}

/** A Runner for single Table Generators */
type GeneratorRunner interface {
	// Run the Generator
	// Inputs: the main input data
	Run(inputs []element.StreamReader) element.StreamReader
}

type wrappedSingleGeneratorRunner struct {
	runner GeneratorRunner
}

func (w wrappedSingleGeneratorRunner) Run(input []element.StreamReader, output []element.StreamWriter) error {
	if len(output) != 1 {
		return errors.New("Only one output supported")
	}
	singleOutput := output[0]
	result := w.runner.Run(input)
	for {
		element, err := result.Read()
		if err == io.EOF {
			// Done
			return nil
		}
		if err != nil {
			return err
		}
		err = singleOutput.Write(element)
		if err != nil {
			return err
		}
	}
}

func NewMultiGeneratorRunner(ref TableGeneratorProgramRef) (MultiGeneratorRunner, error) {
	switch u := ref.Underlying.(type) {
	case *SplitProgram:
		return newSplitRunner(u)
	}
	// Falling back to SingleGeneratorRunner
	singleRunner, err := NewGeneratorRunner(ref)
	if err != nil {
		return nil, err
	}
	return wrappedSingleGeneratorRunner{singleRunner}, nil
}

func NewGeneratorRunner(ref TableGeneratorProgramRef) (GeneratorRunner, error) {
	// TODO: Check that there are no duplicate inputs, as they won't work with the StreamReader
	switch u := ref.Underlying.(type) {
	case *DataSource:
		return &dataSourceRunner{dataSource: u}, nil
	case *SelectProgram:
		return newSelectRunner(u)
	case *UnionProgram:
		return newUnionRunner(u)
	case *JoinProgram:
		return newJoinRunner(u)
	default:
		return nil, errors.Errorf("Unsupported (single) program type %s, FIXME", u.Type())
	}
}

// Runs a data source
type dataSourceRunner struct {
	dataSource *DataSource
}

func (d *dataSourceRunner) Run(inputs []element.StreamReader) element.StreamReader {
	return inputs[d.dataSource.Port]
}

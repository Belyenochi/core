package selectbridge

import (
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/serving"
	"path"
	"select/services/selectbridge/runner"
)

type SelectBackend struct {
}

func (s *SelectBackend) LoadModel(payloadDirectory *string, mantikHeader serving.MantikHeader) (serving.Executable, error) {
	return LoadModelFromMantikHeader(mantikHeader)
}

func LoadModel(directory string) (*SelectRunner, error) {
	mfPath := path.Join(directory, "MantikHeader")
	mf, err := serving.LoadMantikHeader(mfPath)
	if err != nil {
		return nil, errors.Wrap(err, "Could not read MantikHeader")
	}
	return LoadModelFromMantikHeader(mf)
}

func LoadModelFromMantikHeader(mantikHeader serving.MantikHeader) (*SelectRunner, error) {
	sm, err := ParseSelectMantikHeader(mantikHeader.Json())
	if err != nil {
		return nil, errors.Wrap(err, "Could not decode MantikHeader")
	}
	var selectorRunner *runner.Runner
	if sm.Program.Selector != nil {
		selectorRunner, err = runner.CreateRunner(sm.Program.Selector)
		if err != nil {
			return nil, errors.Wrap(err, "Could not create Runner for selector")
		}
	}
	var projectorRunner *runner.Runner
	if sm.Program.Projector != nil {
		projectorRunner, err = runner.CreateRunner(sm.Program.Projector)
		if err != nil {
			return nil, errors.Wrap(err, "Could not create Runner for Projector")
		}
	}
	return &SelectRunner{
		algorithmType: &sm.Type,
		selector:      selectorRunner,
		projector:     projectorRunner,
	}, nil
}

type SelectRunner struct {
	algorithmType *serving.AlgorithmType
	selector      *runner.Runner
	projector     *runner.Runner
}

func (s *SelectRunner) Cleanup() {
	// nothing to do
}

func (s *SelectRunner) ExtensionInfo() interface{} {
	// nothing to do
	return nil
}

func (s *SelectRunner) Type() *serving.AlgorithmType {
	return s.algorithmType
}

func (s *SelectRunner) NativeType() *serving.AlgorithmType {
	// is the same
	return s.algorithmType
}

func (s *SelectRunner) Execute(rows []element.Element) ([]element.Element, error) {
	result := make([]element.Element, 0)
	for _, row := range rows {
		tabularRow := row.(*element.TabularRow)
		var isSelected = true
		if s.selector != nil { // if there is no selector, we select them all
			selectResult, err := s.selector.Run(tabularRow.Columns)
			if err != nil {
				return nil, err
			}
			isSelected = selectResult[0].(element.Primitive).X.(bool)
		}
		if isSelected {
			if s.projector == nil {
				result = append(result, tabularRow)
			} else {
				projected, err := s.projector.Run(tabularRow.Columns)
				if err != nil {
					return nil, err
				}
				result = append(result, &element.TabularRow{projected})
			}
		}
	}
	return result, nil
}

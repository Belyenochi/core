module select

require (
	github.com/pkg/errors v0.8.1
	github.com/stretchr/testify v1.3.0
	gl.ambrosys.de/mantik/go_shared v0.0.0
)

replace gl.ambrosys.de/mantik/go_shared => ../../go_shared

replace gl.ambrosys.de/mantik/core/mnp/mnpgo => ../../mnp/mnpgo

go 1.13

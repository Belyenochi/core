/*
 * This file is part of the Mantik Project.
 * Copyright (c) 2020-2021 Mantik UG (Haftungsbeschränkt)
 * Authors: See AUTHORS file
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.
 *
 * Additionally, the following linking exception is granted:
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with other code, such other code is not for that reason
 * alone subject to any of the requirements of the GNU Affero GPL
 * version 3.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license.
 */
package adapt

import (
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"testing"
)

func TestPrimitiveVoidConversion(t *testing.T) {
	converter, err := LookupAutoAdapter(ds.String, ds.Void)
	assert.NoError(t, err)
	converted, err := converter(element.Primitive{"Hello World"})
	assert.Equal(t, element.Primitive{nil}, converted)
}

func TestComplexVoidConversion(t *testing.T) {
	from := ds.FromJsonStringOrPanic(`{"type":"tensor", "shape": [1,2], "componentType": "uint8"}`)
	converter, err := LookupAutoAdapter(from, ds.Void)
	assert.NoError(t, err)
	converted, err := converter(&element.TensorElement{[]int32{1, 2}})
	assert.Equal(t, element.Primitive{nil}, converted)
}

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
package adapter

import (
	"github.com/mantik-ai/core/go_shared/ds"
	"github.com/mantik-ai/core/go_shared/ds/adapt"
	"github.com/mantik-ai/core/go_shared/ds/element"
	"github.com/pkg/errors"
)

/** Adapts rows of Strings to tabular rows */
type RowAdapter struct {
	adapters []adapt.Adapter
}

func NewRowAdapter(tabularData *ds.TabularData) (*RowAdapter, error) {
	size := tabularData.Columns.Arity()
	adapters := make([]adapt.Adapter, size, size)
	for i, dt := range tabularData.Columns.Values {
		cast, err := adapt.LookupCast(ds.String, dt.SubType.Underlying)
		if err != nil {
			return nil, errors.Wrapf(err, "Could not adapt %s", dt.Name)
		}
		adapters[i] = cast.Adapter
	}
	return &RowAdapter{adapters}, nil
}

func (r *RowAdapter) Adapter(strings []string) (*element.TabularRow, error) {
	size := len(r.adapters)
	elements := make([]element.Element, size, size)
	for i := 0; i < size; i++ {
		e, err := r.adapters[i](element.Primitive{strings[i]})
		if err != nil {
			return nil, err
		}
		elements[i] = e
	}
	return &element.TabularRow{elements}, nil
}

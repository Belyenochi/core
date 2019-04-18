// Code generated by protoc-gen-go. DO NOT EDIT.
// source: tensorflow/core/framework/function.proto

package framework // import "tfbridge/pb_vendor/github.com/tensorflow/tensorflow/tensorflow/go/core/framework"

import proto "github.com/golang/protobuf/proto"
import fmt "fmt"
import math "math"

// Reference imports to suppress errors if they are not otherwise used.
var _ = proto.Marshal
var _ = fmt.Errorf
var _ = math.Inf

// This is a compile-time assertion to ensure that this generated file
// is compatible with the proto package it is being compiled against.
// A compilation error at this line likely means your copy of the
// proto package needs to be updated.
const _ = proto.ProtoPackageIsVersion2 // please upgrade the proto package

// A library is a set of named functions.
type FunctionDefLibrary struct {
	Function             []*FunctionDef `protobuf:"bytes,1,rep,name=function,proto3" json:"function,omitempty"`
	Gradient             []*GradientDef `protobuf:"bytes,2,rep,name=gradient,proto3" json:"gradient,omitempty"`
	XXX_NoUnkeyedLiteral struct{}       `json:"-"`
	XXX_unrecognized     []byte         `json:"-"`
	XXX_sizecache        int32          `json:"-"`
}

func (m *FunctionDefLibrary) Reset()         { *m = FunctionDefLibrary{} }
func (m *FunctionDefLibrary) String() string { return proto.CompactTextString(m) }
func (*FunctionDefLibrary) ProtoMessage()    {}
func (*FunctionDefLibrary) Descriptor() ([]byte, []int) {
	return fileDescriptor_function_0b23a3c04dee4c71, []int{0}
}
func (m *FunctionDefLibrary) XXX_Unmarshal(b []byte) error {
	return xxx_messageInfo_FunctionDefLibrary.Unmarshal(m, b)
}
func (m *FunctionDefLibrary) XXX_Marshal(b []byte, deterministic bool) ([]byte, error) {
	return xxx_messageInfo_FunctionDefLibrary.Marshal(b, m, deterministic)
}
func (dst *FunctionDefLibrary) XXX_Merge(src proto.Message) {
	xxx_messageInfo_FunctionDefLibrary.Merge(dst, src)
}
func (m *FunctionDefLibrary) XXX_Size() int {
	return xxx_messageInfo_FunctionDefLibrary.Size(m)
}
func (m *FunctionDefLibrary) XXX_DiscardUnknown() {
	xxx_messageInfo_FunctionDefLibrary.DiscardUnknown(m)
}

var xxx_messageInfo_FunctionDefLibrary proto.InternalMessageInfo

func (m *FunctionDefLibrary) GetFunction() []*FunctionDef {
	if m != nil {
		return m.Function
	}
	return nil
}

func (m *FunctionDefLibrary) GetGradient() []*GradientDef {
	if m != nil {
		return m.Gradient
	}
	return nil
}

// A function can be instantiated when the runtime can bind every attr
// with a value. When a GraphDef has a call to a function, it must
// have binding for every attr defined in the signature.
//
// TODO(zhifengc):
//   * device spec, etc.
type FunctionDef struct {
	// The definition of the function's name, arguments, return values,
	// attrs etc.
	Signature *OpDef `protobuf:"bytes,1,opt,name=signature,proto3" json:"signature,omitempty"`
	// Attributes specific to this function definition.
	Attr map[string]*AttrValue `protobuf:"bytes,5,rep,name=attr,proto3" json:"attr,omitempty" protobuf_key:"bytes,1,opt,name=key,proto3" protobuf_val:"bytes,2,opt,name=value,proto3"`
	// By convention, "op" in node_def is resolved by consulting with a
	// user-defined library first. If not resolved, "func" is assumed to
	// be a builtin op.
	NodeDef []*NodeDef `protobuf:"bytes,3,rep,name=node_def,json=nodeDef,proto3" json:"node_def,omitempty"`
	// A mapping from the output arg names from `signature` to the
	// outputs from `node_def` that should be returned by the function.
	Ret                  map[string]string `protobuf:"bytes,4,rep,name=ret,proto3" json:"ret,omitempty" protobuf_key:"bytes,1,opt,name=key,proto3" protobuf_val:"bytes,2,opt,name=value,proto3"`
	XXX_NoUnkeyedLiteral struct{}          `json:"-"`
	XXX_unrecognized     []byte            `json:"-"`
	XXX_sizecache        int32             `json:"-"`
}

func (m *FunctionDef) Reset()         { *m = FunctionDef{} }
func (m *FunctionDef) String() string { return proto.CompactTextString(m) }
func (*FunctionDef) ProtoMessage()    {}
func (*FunctionDef) Descriptor() ([]byte, []int) {
	return fileDescriptor_function_0b23a3c04dee4c71, []int{1}
}
func (m *FunctionDef) XXX_Unmarshal(b []byte) error {
	return xxx_messageInfo_FunctionDef.Unmarshal(m, b)
}
func (m *FunctionDef) XXX_Marshal(b []byte, deterministic bool) ([]byte, error) {
	return xxx_messageInfo_FunctionDef.Marshal(b, m, deterministic)
}
func (dst *FunctionDef) XXX_Merge(src proto.Message) {
	xxx_messageInfo_FunctionDef.Merge(dst, src)
}
func (m *FunctionDef) XXX_Size() int {
	return xxx_messageInfo_FunctionDef.Size(m)
}
func (m *FunctionDef) XXX_DiscardUnknown() {
	xxx_messageInfo_FunctionDef.DiscardUnknown(m)
}

var xxx_messageInfo_FunctionDef proto.InternalMessageInfo

func (m *FunctionDef) GetSignature() *OpDef {
	if m != nil {
		return m.Signature
	}
	return nil
}

func (m *FunctionDef) GetAttr() map[string]*AttrValue {
	if m != nil {
		return m.Attr
	}
	return nil
}

func (m *FunctionDef) GetNodeDef() []*NodeDef {
	if m != nil {
		return m.NodeDef
	}
	return nil
}

func (m *FunctionDef) GetRet() map[string]string {
	if m != nil {
		return m.Ret
	}
	return nil
}

// GradientDef defines the gradient function of a function defined in
// a function library.
//
// A gradient function g (specified by gradient_func) for a function f
// (specified by function_name) must follow the following:
//
// The function 'f' must be a numerical function which takes N inputs
// and produces M outputs. Its gradient function 'g', which is a
// function taking N + M inputs and produces N outputs.
//
// I.e. if we have
//    (y1, y2, ..., y_M) = f(x1, x2, ..., x_N),
// then, g is
//    (dL/dx1, dL/dx2, ..., dL/dx_N) = g(x1, x2, ..., x_N,
//                                      dL/dy1, dL/dy2, ..., dL/dy_M),
// where L is a scalar-value function of (x1, x2, ..., xN) (e.g., the
// loss function). dL/dx_i is the partial derivative of L with respect
// to x_i.
type GradientDef struct {
	FunctionName         string   `protobuf:"bytes,1,opt,name=function_name,json=functionName,proto3" json:"function_name,omitempty"`
	GradientFunc         string   `protobuf:"bytes,2,opt,name=gradient_func,json=gradientFunc,proto3" json:"gradient_func,omitempty"`
	XXX_NoUnkeyedLiteral struct{} `json:"-"`
	XXX_unrecognized     []byte   `json:"-"`
	XXX_sizecache        int32    `json:"-"`
}

func (m *GradientDef) Reset()         { *m = GradientDef{} }
func (m *GradientDef) String() string { return proto.CompactTextString(m) }
func (*GradientDef) ProtoMessage()    {}
func (*GradientDef) Descriptor() ([]byte, []int) {
	return fileDescriptor_function_0b23a3c04dee4c71, []int{2}
}
func (m *GradientDef) XXX_Unmarshal(b []byte) error {
	return xxx_messageInfo_GradientDef.Unmarshal(m, b)
}
func (m *GradientDef) XXX_Marshal(b []byte, deterministic bool) ([]byte, error) {
	return xxx_messageInfo_GradientDef.Marshal(b, m, deterministic)
}
func (dst *GradientDef) XXX_Merge(src proto.Message) {
	xxx_messageInfo_GradientDef.Merge(dst, src)
}
func (m *GradientDef) XXX_Size() int {
	return xxx_messageInfo_GradientDef.Size(m)
}
func (m *GradientDef) XXX_DiscardUnknown() {
	xxx_messageInfo_GradientDef.DiscardUnknown(m)
}

var xxx_messageInfo_GradientDef proto.InternalMessageInfo

func (m *GradientDef) GetFunctionName() string {
	if m != nil {
		return m.FunctionName
	}
	return ""
}

func (m *GradientDef) GetGradientFunc() string {
	if m != nil {
		return m.GradientFunc
	}
	return ""
}

func init() {
	proto.RegisterType((*FunctionDefLibrary)(nil), "tensorflow.FunctionDefLibrary")
	proto.RegisterType((*FunctionDef)(nil), "tensorflow.FunctionDef")
	proto.RegisterMapType((map[string]*AttrValue)(nil), "tensorflow.FunctionDef.AttrEntry")
	proto.RegisterMapType((map[string]string)(nil), "tensorflow.FunctionDef.RetEntry")
	proto.RegisterType((*GradientDef)(nil), "tensorflow.GradientDef")
}

func init() {
	proto.RegisterFile("tensorflow/core/framework/function.proto", fileDescriptor_function_0b23a3c04dee4c71)
}

var fileDescriptor_function_0b23a3c04dee4c71 = []byte{
	// 417 bytes of a gzipped FileDescriptorProto
	0x1f, 0x8b, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02, 0xff, 0x7c, 0x92, 0xdf, 0xab, 0xd3, 0x30,
	0x14, 0xc7, 0x69, 0xbb, 0xab, 0xed, 0xe9, 0x55, 0xae, 0x51, 0x31, 0xf4, 0x69, 0x4e, 0x90, 0xa1,
	0xd0, 0xc2, 0x2e, 0x8a, 0x08, 0x3e, 0x38, 0xfc, 0x01, 0x22, 0x73, 0xf4, 0x41, 0xc1, 0x97, 0x92,
	0xad, 0x69, 0x2d, 0x5b, 0x93, 0x91, 0xa5, 0x8e, 0xbd, 0xf8, 0xef, 0xfa, 0x2f, 0xf8, 0x28, 0x49,
	0x9b, 0x35, 0xe8, 0x7a, 0xdf, 0x42, 0xf2, 0xf9, 0x7e, 0xbf, 0x27, 0xe7, 0x1c, 0x98, 0x4a, 0xca,
	0xf6, 0x5c, 0x14, 0x5b, 0x7e, 0x48, 0xd6, 0x5c, 0xd0, 0xa4, 0x10, 0xa4, 0xa6, 0x07, 0x2e, 0x36,
	0x49, 0xd1, 0xb0, 0xb5, 0xac, 0x38, 0x8b, 0x77, 0x82, 0x4b, 0x8e, 0xa0, 0x27, 0xa3, 0x67, 0xc3,
	0x2a, 0x22, 0xa5, 0xc8, 0x7e, 0x92, 0x6d, 0x43, 0x5b, 0x5d, 0x74, 0x43, 0x02, 0xe3, 0x39, 0xcd,
	0x72, 0x5a, 0x74, 0xe4, 0xd3, 0x61, 0x92, 0xef, 0x7a, 0x6e, 0xf2, 0x0b, 0xd0, 0x87, 0xae, 0xb6,
	0x77, 0xb4, 0xf8, 0x5c, 0xad, 0x04, 0x11, 0x47, 0x74, 0x0d, 0xbe, 0xa9, 0x18, 0x3b, 0x63, 0x6f,
	0x1a, 0xce, 0x1e, 0xc5, 0xbd, 0x61, 0x6c, 0x29, 0xd2, 0x13, 0xa8, 0x44, 0xa5, 0x20, 0x79, 0x45,
	0x99, 0xc4, 0xee, 0xff, 0xa2, 0x8f, 0xdd, 0x9b, 0x16, 0x19, 0x70, 0xf2, 0xdb, 0x85, 0xd0, 0xb2,
	0x43, 0x09, 0x04, 0xfb, 0xaa, 0x64, 0x44, 0x36, 0x82, 0x62, 0x67, 0xec, 0x4c, 0xc3, 0xd9, 0x3d,
	0xdb, 0xe5, 0xcb, 0x4e, 0xe9, 0x7b, 0x06, 0xbd, 0x80, 0x91, 0x6a, 0x13, 0xbe, 0xd0, 0x89, 0x8f,
	0x07, 0xca, 0x8c, 0xdf, 0x4a, 0x29, 0xde, 0x33, 0x29, 0x8e, 0xa9, 0xc6, 0x51, 0x0c, 0xbe, 0xe9,
	0x18, 0xf6, 0xb4, 0xf4, 0xbe, 0x2d, 0x5d, 0xf0, 0x9c, 0xaa, 0xa0, 0xdb, 0xac, 0x3d, 0xa0, 0x19,
	0x78, 0x82, 0x4a, 0x3c, 0xd2, 0xe8, 0x78, 0x28, 0x25, 0xa5, 0xb2, 0x0d, 0x51, 0x70, 0xb4, 0x80,
	0xe0, 0x14, 0x8b, 0xae, 0xc0, 0xdb, 0xd0, 0xa3, 0xfe, 0x52, 0x90, 0xaa, 0x23, 0x7a, 0x0e, 0x17,
	0x7a, 0xb6, 0xd8, 0xd5, 0xdf, 0x7c, 0x68, 0x9b, 0x2a, 0xdd, 0x57, 0xf5, 0x98, 0xb6, 0xcc, 0x6b,
	0xf7, 0x95, 0x13, 0xbd, 0x04, 0xdf, 0x04, 0x9c, 0xb1, 0x7b, 0x60, 0xdb, 0x05, 0x96, 0xee, 0xd3,
	0xc8, 0x77, 0xaf, 0xbc, 0xc9, 0x37, 0x08, 0xad, 0x11, 0xa0, 0x27, 0x70, 0xc7, 0x4c, 0x2e, 0x63,
	0xa4, 0xa6, 0x9d, 0xd5, 0xa5, 0xb9, 0x5c, 0x90, 0x9a, 0x2a, 0xc8, 0x4c, 0x2a, 0x53, 0x0f, 0x9d,
	0xf7, 0xa5, 0xb9, 0x54, 0x7f, 0x9f, 0x33, 0xc0, 0x5c, 0x94, 0x76, 0xf5, 0xa7, 0x5d, 0x9b, 0xdf,
	0x35, 0xdd, 0x59, 0xaa, 0x6d, 0xdb, 0x2f, 0x9d, 0xef, 0x6f, 0xca, 0x4a, 0xfe, 0x68, 0x56, 0xf1,
	0x9a, 0xd7, 0x89, 0xb5, 0xa3, 0xe7, 0x8f, 0x25, 0xff, 0x67, 0x79, 0xff, 0x38, 0xce, 0xea, 0x96,
	0xde, 0xdc, 0xeb, 0xbf, 0x01, 0x00, 0x00, 0xff, 0xff, 0x5f, 0x3b, 0xaa, 0x8e, 0x6f, 0x03, 0x00,
	0x00,
}

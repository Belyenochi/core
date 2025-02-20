NAME=tfbridge
EXTRA_DEPS=target/vendor/tensorflow_c/lib target/docker_build/generated.make
UNAME_S?=$(shell uname -s)
CLIBDIR = target/vendor/tensorflow_c
TENSORFLOW_DIR = $(CLIBDIR)/lib
ENABLE_LINUX=false
DOCKER_IMAGE_NAME=bridge.tf.saved_model

# Linux C-Library
CLIBURL_LINUX=https://storage.googleapis.com/tensorflow/libtensorflow/libtensorflow-cpu-linux-x86_64-1.15.0.tar.gz

# This makefile is a bit ugly because we need the C-Library linked to the Executable.

ifeq ($(UNAME_S), Darwin)
  DYLD_LIBRARY_PATH=$(shell pwd)/$(TENSORFLOW_DIR)
  export DYLD_LIBRARY_PATH
  CLIBURL=https://storage.googleapis.com/tensorflow/libtensorflow/libtensorflow-cpu-darwin-x86_64-1.15.0.tar.gz
else ifeq ($(UNAME_S), Linux)
  LD_LIBRARY_PATH=$(shell pwd)/$(TENSORFLOW_DIR)
  $(info LD_LIBRARY_PATH=$(LD_LIBRARY_PATH))
  export LD_LIBRARY_PATH
  CLIBURL=$(CLIBURL_LINUX)
else
  $(error Unknown platform)
endif

CGO_LDFLAGS = -L$(shell pwd)/$(TENSORFLOW_DIR)
export CGO_LDFLAGS
$(info CGO_LDFLAGS=$(CGO_LDFLAGS))

MANTIK_ROOT = ../../../

include $(MANTIK_ROOT)/scripts/ci/Makefile.go
include $(MANTIK_ROOT)/scripts/ci/Makefile.docker_single

ifdef CACHE_DIR
  DOWNLOAD_CACHE=$(CACHE_DIR)/download
else
  DOWNLOAD_CACHE=$(shell pwd)/target
endif

clean::
	rm -rf target

# Pulling dependencies
pull_dependencies: target/vendor/tensorflow_c/lib

target/vendor/tensorflow_c/lib:
	mkdir -p $(CLIBDIR)
	mkdir -p $(DOWNLOAD_CACHE)
	wget -nc $(CLIBURL) -P $(DOWNLOAD_CACHE)
	wget -nc $(CLIBURL_LINUX) -P $(DOWNLOAD_CACHE) # Must be present for Docker
	tar xvzf $(DOWNLOAD_CACHE)/$(shell basename $(CLIBURL)) -C $(CLIBDIR)

# Prepare Docker image build directory, referenced the Dockerfile
# (In our dockerfile the target is rebuild, for stabilizing the linkage)
target/docker_build/generated.make: $(shell find $(MANTIK_ROOT)/go_shared -name *.go)
	mkdir -p target/docker_build
	cp -r $(MANTIK_ROOT)/go_shared target/docker_build/
	cp -r $(MANTIK_ROOT)/mnp target/docker_build/
	cp $(DOWNLOAD_CACHE)/$(shell basename $(CLIBURL_LINUX)) target/docker_build
	touch $@

# Finagle Protobuf

[![Build status](https://img.shields.io/travis/finagle/finagle-protobuf/master.svg)](http://travis-ci.org/finagle/finagle-protobuf) [![Coverage status](https://img.shields.io/coveralls/finagle/finagle-protobuf/master.svg)](https://coveralls.io/r/finagle/finagle-protobuf?branch=master)

This repository is the new home of `finagle-protobuf`, which used to be a
[Finagle][finagle] subproject. It includes the work of
[George Vacariuc][vacariuc] and others that was reviewed in
[this pull request][pr91].

[finagle]: https://github.com/twitter/finagle
[vacariuc]: https://github.com/george-vacariuc
[pr91]: https://github.com/twitter/finagle/pull/91


## Status

This branch has WIP from the internal Tendril version of finagle-protobuf. It is being published herefor initial review purposes.

At a minimum, the following work will likely need to be done before this is a candidate for merge:
- [ ] Reconcile the builds and make sure this builds publicly
- [ ] Determine and modify structure - we have two submodules but the published just has one and is a directory down
- [ ] Update to appropriate finagle version
- [ ] Make sure I've provided all the Tendril stuff and good examples to be usable and complete  

## License

Licensed under the **[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)** (the "License");
you may not use this software except in compliance with the License.

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

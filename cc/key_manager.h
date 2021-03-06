// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
///////////////////////////////////////////////////////////////////////////////

#include <algorithm>
#include <vector>

#ifndef TINK_KEY_MANAGER_H_
#define TINK_KEY_MANAGER_H_

#include "absl/strings/string_view.h"
#include "cc/util/errors.h"
#include "cc/util/status.h"
#include "cc/util/statusor.h"
#include "google/protobuf/message.h"
#include "proto/tink.pb.h"

namespace crypto {
namespace tink {

// An auxiliary container for methods that generate new key material.
// These methods are grouped separately, as their functionality
// is independent of the primitive of the corresponding KeyManager.
class KeyFactory {
 public:
  // Generates a new random key, based on the specified 'key_format'.
  virtual
  crypto::tink::util::StatusOr<std::unique_ptr<google::protobuf::Message>>
  NewKey(const google::protobuf::Message& key_format) const = 0;

  // Generates a new random key, based on the specified 'serialized_key_format'.
  virtual
  crypto::tink::util::StatusOr<std::unique_ptr<google::protobuf::Message>>
  NewKey(absl::string_view serialized_key_format) const = 0;

  // Generates a new random key, based on the specified 'serialized_key_format',
  // and wraps it in a KeyData-proto.
  virtual
  crypto::tink::util::StatusOr<std::unique_ptr<google::crypto::tink::KeyData>>
  NewKeyData(absl::string_view serialized_key_format) const = 0;
};

/**
 * KeyManager "understands" keys of a specific key types: it can
 * generate keys of a supported type and create primitives for
 * supported keys.  A key type is identified by the global name of the
 * protocol buffer that holds the corresponding key material, and is
 * given by type_url-field of KeyData-protocol buffer.
 *
 * - P: the primitive implemented by keys understood by this manager
 */
template<class P>
class KeyManager {
 public:
  // Constructs an instance of P for the given 'key_data'.
  virtual crypto::tink::util::StatusOr<std::unique_ptr<P>>
  GetPrimitive(const google::crypto::tink::KeyData& key_data) const = 0;

  // Constructs an instance of P for the given 'key'.
  virtual crypto::tink::util::StatusOr<std::unique_ptr<P>>
  GetPrimitive(const google::protobuf::Message& key) const = 0;

  // Returns the type_url identifying the key type handled by this manager.
  virtual const std::string& get_key_type() const = 0;

  // Returns the version of this key manager.
  virtual uint32_t get_version() const = 0;

  // Returns a factory that generates keys of the key type
  // handled by this manager.
  virtual const KeyFactory& get_key_factory() const = 0;

  bool DoesSupport(absl::string_view key_type) const {
    return (key_type == get_key_type());
  }

  virtual ~KeyManager<P>() {}
};

}  // namespace tink
}  // namespace crypto

#endif  // TINK_KEY_MANAGER_H_

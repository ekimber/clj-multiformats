(ns multiformats.base
  "Multibase is a protocol for distinguishing base encodings and other simple
  string encodings, and for ensuring full compatibility with program
  interfaces.

  https://github.com/multiformats/multibase"
  (:refer-clojure :exclude [bases format])
  #?(:cljs (:require-macros multiformats.base))
  (:require
    [alphabase.bytes :as b]
    [alphabase.core :as abc]
    [clojure.string :as str])
  #?(:clj
     (:import
       (org.apache.commons.codec.binary
         Base32
         Base64
         BinaryCodec
         Hex))))


(def codes
  "Map of base keys to multicodec packed symbols from the standard table."
  {; Numeric
   :base1         \1
   :base2         \0
   :base8         \7
   :base10        \9
   ; Hexadecimal (RFC 4648)
   :base16        \f
   :BASE16        \F
   ; Base32 (RFC 4648)
   :base32        \b
   :BASE32        \B
   :base32pad     \c
   :BASE32PAD     \C
   :base32hex     \v
   :BASE32HEX     \V
   :base32hexpad  \t
   :BASE32HEXPAD  \T
   ; Base58
   :base58btc     \z
   :base58flickr  \Z
   ; Base64 (RFC 4648)
   :base64        \m
   :base64pad     \M
   :base64url     \u
   :base64urlpad  \U})



;; ## Base Encodings

(defmacro ^:private defbase
  "Define a new base map in a var."
  [base-sym & {:as params}]
  `(def ~(vary-meta base-sym assoc :private true)
     (assoc ~params :key ~(keyword base-sym))))


;; ### Binary

(defn- reverse-octets
  "Reverse the octets (8-bit segments) a binary string."
  [string]
  (loop [octets nil
         i 0]
    (if (< i (count string))
      (recur (cons (subs string i (+ i 8)) octets) (+ i 8))
      (apply str octets))))


(defn- format-binary
  "Format some byte data into a binary-encoded string."
  [^bytes data]
  #?(:clj
     (reverse-octets (BinaryCodec/toAsciiString data))
     :default
     (reduce
       (fn build-str
         [string i]
         (let [x (b/get-byte data i)
               octet (str (if (zero? (bit-and x 0x80)) "0" "1")
                          (if (zero? (bit-and x 0x40)) "0" "1")
                          (if (zero? (bit-and x 0x20)) "0" "1")
                          (if (zero? (bit-and x 0x10)) "0" "1")
                          (if (zero? (bit-and x 0x08)) "0" "1")
                          (if (zero? (bit-and x 0x04)) "0" "1")
                          (if (zero? (bit-and x 0x02)) "0" "1")
                          (if (zero? (bit-and x 0x01)) "0" "1"))]
           (str string octet)))
       "" (range (alength data)))))


(defn- parse-binary
  "Parse a string of binary-encoded bytes."
  [^String string]
  #?(:clj
     (.toByteArray (BinaryCodec.) (reverse-octets string))
     :default
     (let [string (if (zero? (rem (count string) 8))
                    string
                    (str (str/join (repeat (- 8 (rem (count string) 8)) "0"))
                         string))
           buffer (b/byte-array (int (/ (count string) 8)))]
       (dotimes [i (alength buffer)]
         (let [octet (subs string (* i 8) (* (inc i) 8))
               x (bit-or (if (= "1" (subs octet 0 1)) 0x80 0x00)
                         (if (= "1" (subs octet 1 2)) 0x40 0x00)
                         (if (= "1" (subs octet 2 3)) 0x20 0x00)
                         (if (= "1" (subs octet 3 4)) 0x10 0x00)
                         (if (= "1" (subs octet 4 5)) 0x08 0x00)
                         (if (= "1" (subs octet 5 6)) 0x04 0x00)
                         (if (= "1" (subs octet 6 7)) 0x02 0x00)
                         (if (= "1" (subs octet 7 8)) 0x01 0x00))]
           (b/set-byte buffer i x)))
       buffer)))


(defbase base2
  :formatter format-binary
  :parser parse-binary)


;; ### Octal

(defbase base8
  :alphabet "01234567")


;; ### Decimal

(defbase base10
  :formatter (fn format-decimal
               [^bytes data]
               ; TODO: implement
               ,,,)
  :parser (fn parse-decimal
            [^String string]
            ; TODO: implement
            ,,,
            ))


;; ### Hexadecimal

(defn- format-hex
  "Format byte data as a hexadecimal-encoded string."
  [^bytes data]
  #?(:clj
     (Hex/encodeHexString data true)
     :default
     (abc/encode "0123456789abcdef" data)))


(defn- parse-hex
  "Parse a hexadecimal-encoded string into bytes."
  [^String string]
  #?(:clj
     (Hex/decodeHex string)
     :default
     (abc/decode "0123456789abcdef" (str/lower-case string))))


(defbase base16
  :formatter format-hex
  :parser parse-hex)


(defbase BASE16
  :formatter (comp str/upper-case format-hex)
  :parser parse-hex)


;; ### Base32 (RFC 4648)

(defn- base32-formatter
  "Constructs a function which formats byte data as a base32-encoded string."
  [hex? lower? pad?]
  #?(:clj
     (let [codec (Base32. 0 nil hex? (int \=))]
       (fn format
         [^bytes data]
         (cond-> (.encodeToString codec data)
           lower? (str/lower-case)
           (not pad?) (str/replace #"=+$" ""))))))


(defn- base32-parser
  "Constructs a function which parses a base32-encoded string into bytes."
  [hex?]
  #?(:clj
     (let [codec (Base32. 0 nil hex? (int \=))]
       (fn parse
         [^String string]
         (.decode codec string)))))


(defbase base32
  :formatter (base32-formatter false true false)
  :parser (base32-parser false))


(defbase BASE32
  :formatter (base32-formatter false false false)
  :parser (base32-parser false))


(defbase base32pad
  :formatter (base32-formatter false true true)
  :parser (base32-parser false))


(defbase BASE32PAD
  :formatter (base32-formatter false false true)
  :parser (base32-parser false))


(defbase base32hex
  :formatter (base32-formatter true true false)
  :parser (base32-parser true))


(defbase BASE32HEX
  :formatter (base32-formatter true false false)
  :parser (base32-parser true))


(defbase base32hexpad
  :formatter (base32-formatter true true true)
  :parser (base32-parser true))


(defbase BASE32HEXPAD
  :formatter (base32-formatter true false true)
  :parser (base32-parser true))



;; ### Base58

(defbase base58btc
  :alphabet "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz")


;; ### Base64 (RFC 4648)

(defn- base64-formatter
  "Construct a new function to format bytes as base64 with normal or URL
  alphabet, padded or not."
  [url? padding?]
  (fn format
    [^bytes data]
    ; TODO: cljs implementation
    #?(:clj
       (let [encoded (if url?
                       (Base64/encodeBase64URLSafeString data)
                       (Base64/encodeBase64String data))]
         (cond
           (and url? padding?)
           (str encoded (case (rem (alength data) 3)
                          2 "="
                          1 "=="
                          nil))

           (and (not url?) (not padding?))
           (str/replace encoded #"=+$" "")

           :else encoded)))))


(defn- parse-base64
  "Parse a string of base64-encoded bytes."
  [^String string]
  ; TODO: cljs implementation
  #?(:clj
     (Base64/decodeBase64 string)))


(defbase base64
  :formatter (base64-formatter false false)
  :parser parse-base64)


(defbase base64pad
  :formatter (base64-formatter false true)
  :parser parse-base64)


(defbase base64url
  :formatter (base64-formatter true false)
  :parser parse-base64)


(defbase base64urlpad
  :formatter (base64-formatter true true)
  :parser parse-base64)



;; ## Lookup Functions

(defn- base-formatter
  "Construct an encoding function from base parameters."
  [params]
  (or (:formatter params)
      (when-let [alphabet (:alphabet params)]
        (fn format-alphabet
          [data]
          (abc/encode alphabet data)))
      (throw (ex-info (str "Base " (:key params)
                           " does not specify an alphabet "
                           " or a formatter function.")
                      {:base (:key params)}))))


(defn- base-parser
  "Construct a decoding function from base params."
  [params]
  (or (:parser params)
      (when-let [alphabet (:alphabet params)]
        (fn parse-alphabet
          [string]
          (abc/decode alphabet string)))
      (throw (ex-info (str "Base " (:key params)
                           " does not specify an alphabet "
                           " or a parser function.")
                      {:base (:key params)}))))


(defn- install-base
  "Expands a base definition map into one or more definitions and adds them to
  the given map of bases. Returns the updated map, or throws an exception if
  there is a conflict or insufficient information."
  [base-map params]
  (let [base-key (:key params)
        prefix (some-> (get codes base-key) str)]
    (when-not (keyword? base-key)
      (throw (ex-info (str "Base registered with invalid key: "
                           (pr-str base-key))
                      {:base base-key})))
    (when-not prefix
      (throw (ex-info (str "Base " base-key " has no assigned prefix code!")
                      {:base base-key})))
    (when (contains? base-map base-key)
      (throw (ex-info (str "Base " base-key " is already registered!")
                      {:base base-key})))
    (when-let [extant (ffirst (filter (comp #{prefix} :prefix val) base-map))]
      (throw (ex-info (str "Prefix " (pr-str prefix)
                           " is already registered to " extant)
                      {:base base-key
                       :prefix prefix})))
    (assoc base-map
           base-key
           (-> params
               (dissoc :key)
               (assoc :prefix prefix
                      :formatter (base-formatter params)
                      :parser (base-parser params))))))


(def bases
  "Map of base keys to definition maps."
  (reduce install-base
          {}
          [base2
           base8
           ;base10
           base16
           BASE16
           base32
           BASE32
           base32pad
           BASE32PAD
           base32hex
           BASE32HEX
           base32hexpad
           BASE32HEXPAD
           base58btc
           base64
           base64pad
           base64url
           base64urlpad]))


(def ^:private prefix->base
  "Cached map of prefix characters to base keys."
  (into {} (map (juxt (comp :prefix val) key)) bases))



;; ## Encoding

(defn format*
  "Formats binary data into a string with the given base. Returns the formatted
  string without a prefix."
  ^String
  [base-key ^bytes data]
  (when-not (keyword? base-key)
    (throw (ex-info "base-key must be a keyword" {:base base-key})))
  (when (zero? (alength data))
    (throw (ex-info "Cannot format empty data as a multibase string"
                    {:base base-key})))
  (let [formatter (get-in bases [base-key :formatter])]
    (when-not formatter
      (throw (ex-info (str (name base-key) " does not have a supported multibase formatter")
                      {:base base-key})))
    (formatter data)))


(defn format
  "Formats binary data into a string with the given base. Returns the formatted
  string, prefixed with the base constant."
  ^String
  [base-key ^bytes data]
  (let [prefix (get codes base-key)]
    (str prefix (format* base-key data))))



;; ## Decoding

(defn- get-prefix
  "Get the multibase prefix character from the given string. Throws an
  exception if the string is too short."
  [^String string]
  (when (< (count string) 2)
    (throw (ex-info (str "The string " (pr-str string)
                         " is too short to be multibase-formatted data")
                    {:data string})))
  (str (first string)))


(defn parse*
  "Parse a non-prefixed string into binary data using the given base. Returns
  the decoded byte array."
  ^bytes
  [base-key string]
  (when-not (keyword? base-key)
    (throw (ex-info "base-key must be a keyword" {:base base-key})))
  (let [parser (get-in bases [base-key :parser])]
    (when-not parser
      (throw (ex-info (str (name base-key) " does not have a supported multibase parser")
                      {:base base-key})))
    (parser string)))


(defn parse
  "Parses a multibase-prefixed string into binary data. Returns an array with
  the parsed bytes, or throws an error if there is no known base."
  ^bytes
  [^String string]
  (let [prefix (get-prefix string)
        base-key (prefix->base prefix)]
    (when-not base-key
      (throw (ex-info (str "The prefix " (pr-str prefix)
                           " does not map to a supported multibase encoding")
                      {:data string
                       :prefix prefix})))
    (parse* base-key (subs string 1))))


(defn inspect
  "Inspect a string and return a map of information including the prefix
  character, base key, and parsed data."
  [^String string]
  (let [prefix (get-prefix string)]
    {:prefix prefix
     :base (prefix->base prefix)
     :data (parse string)}))

(ns org.zalando.stups.mint.storage.api-test
  (:require [clojure.test :refer :all]
            [org.zalando.stups.friboo.auth :as auth]
            [org.zalando.stups.mint.storage.api :as api]
            [org.zalando.stups.mint.test-utils :refer :all]))

(def test-config
  {:team-service-url "https://example.org"
   :kio-url          "https://example.org"
   :service-user-url "https://example.org"
   :allowed-uids     "robo1,robo2"})

(def human-token
  {"uid" "mister-blue"
   "realm" "/employees"
   "scope" ["uid"]})

(def robot-token
  {"uid" "robo"
   "realm" "/services"
   "scope" ["uid" "application.write_sensitive"]})

(def robo1-token
  {"uid"   "robo1"
   "realm" "/services"
   "scope" ["uid" "application.write_all_sensitive"]})

(def robo2-token
  {"uid"   "robo2"
   "realm" "/services"
   "scope" ["uid"]})

(def robo3-token
  {"uid"   "robo3"
   "realm" "/services"
   "scope" ["uid" "application.write_all_sensitive"]})

(deftest require-write-authorization
  (testing "a human needs to be in correct team"
    (let [request {:configuration test-config
                   :tokeninfo human-token}]
      (with-redefs [auth/get-auth (constantly true)]
        (api/require-write-access-for "dogs" request))))

  (testing "a human cannot write without proper team membership"
    (let [request {:configuration test-config
                   :tokeninfo human-token}]
      (with-redefs [auth/get-auth (constantly false)]
        (try
          (api/require-write-access-for "dogs" request)
          (is false)
          (catch Exception ex
            (same! 403 (:http-code (ex-data ex))))))))

  (testing "a robot needs to be in correct team and have write scope"
    (let [request {:configuration test-config
                   :tokeninfo robot-token}]
      (with-redefs [auth/get-auth (constantly true)]
        (api/require-write-access-for "dogs" request))))

  (testing "a robot cannot write without scope"
    (let [request {:configuration test-config
                   :tokeninfo (assoc robot-token "scope" ["uid"])}]
      (with-redefs [auth/get-auth (constantly true)]
        (try
          (api/require-write-access-for "dogs" request)
          (is false)
          (catch Exception ex
            (same! 403 (:http-code (ex-data ex))))))))

  (testing "a robot cannot write without proper team membership"
    (let [request {:configuration test-config
                   :tokeninfo robot-token}]
      (with-redefs [auth/get-auth (constantly false)]
        (try
          (api/require-write-access-for "dogs" request)
          (is false)
          (catch Exception ex
            (same! 403 (:http-code (ex-data ex))))))))

  (testing "a white-listed robot with write_all scope has write permissions"
    (let [request {:configuration test-config
                   :tokeninfo     robo1-token}]
      (with-redefs [auth/get-auth (constantly false)]
        (api/require-write-access-for "dogs" request))))

  (testing "a robot with write_all scope, but not white-listed, has no write permissions"
    (let [request {:configuration test-config
                   :tokeninfo     robo2-token}]
      (with-redefs [auth/get-auth (constantly false)]
        (try
          (api/require-write-access-for "dogs" request)
          (is false)
          (catch Exception ex
            (same! 403 (:http-code (ex-data ex))))))))

  (testing "a white-listed robot without the write_all scope, has no write permissions"
    (let [request {:configuration test-config
                   :tokeninfo     robo3-token}]
      (with-redefs [auth/get-auth (constantly false)]
        (try
          (api/require-write-access-for "dogs" request)
          (is false)
          (catch Exception ex
            (same! 403 (:http-code (ex-data ex)))))))))

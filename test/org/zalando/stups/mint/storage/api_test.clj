(ns org.zalando.stups.mint.storage.api-test
  (:require [clojure.test :refer :all]
            [org.zalando.stups.friboo.user :as fuser]
            [org.zalando.stups.mint.storage.api :as api]
            [org.zalando.stups.mint.test-utils :refer :all]))

(def test-config
  {:team-service-url "https://example.org"
   :service-user-url "https://example.org"})

(def human-token
  {"uid" "mister-blue"
   "realm" "employees"
   "scope" ["uid"]})

(def robot-token
  {"uid" "robo"
   "realm" "/services"
   "scope" ["uid" "application.write_sensitive"]})

(deftest require-write-authorization
  (testing "a human needs to be in correct team"
    (let [request {:configuration test-config
                   :tokeninfo human-token}]
      (with-redefs [fuser/get-teams (constantly [{:name "dogs"}])]
        (api/require-write-access-for "dogs" request))))

  (testing "a human cannot write without proper team membership"
    (let [request {:configuration test-config
                   :tokeninfo human-token}]
      (with-redefs [fuser/get-teams (constantly [{:name "police"}])]
        (try
          (api/require-write-access-for "dogs" request)
          (is false)
          (catch Exception ex
            (same! 403 (:http-code (ex-data ex))))))))

  (testing "a robot needs to be in correct team and have write scope"
    (let [request {:configuration test-config
                   :tokeninfo robot-token}]
      (with-redefs [fuser/get-service-team (constantly "dogs")]
        (api/require-write-access-for "dogs" request))))

  (testing "a robot cannot write without scope"
    (let [request {:configuration test-config
                   :tokeninfo (assoc robot-token "scope" ["uid"])}]
      (with-redefs [fuser/get-service-team (constantly "dogs")]
        (try
          (api/require-write-access-for "dogs" request)
          (is false)
          (catch Exception ex
            (same! 403 (:http-code (ex-data ex))))))))

  (testing "a robot cannot write without proper team membership"
    (let [request {:configuration test-config
                   :tokeninfo robot-token}]
      (with-redefs [fuser/get-service-team (constantly "police")]
        (try
          (api/require-write-access-for "dogs" request)
          (is false)
          (catch Exception ex
            (same! 403 (:http-code (ex-data ex)))))))))

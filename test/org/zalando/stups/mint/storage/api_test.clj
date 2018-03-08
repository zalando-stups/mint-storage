(ns org.zalando.stups.mint.storage.api-test
  (:require [clojure.test :refer :all]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.friboo.auth :as auth]
            [org.zalando.stups.mint.storage.api :as api]
            [org.zalando.stups.friboo.system :as system]
            [org.zalando.stups.mint.test-utils :refer :all]))

(def test-config
  {:team-service-url "http://localhost"
   :kio-url          "http://localhost"
   :essentials-url   "http://localhost"
   :service-user-url "http://localhost"
   :allowed-uids     "robo1,robo2"})

(def human-token
  {"uid"   "mister-blue"
   "realm" "/employees"
   "scope" ["uid"]})

(def robot-token
  {"uid"   "robo"
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

(deftest application-lifecycle
  (let [test-application {:application_id "test-application"
                          :application    {:s3_buckets             ["a-bucket"]
                                           :kubernetes_clusters    ["aws:123123123123:eu-central-1:kube-1" "aws:231231231231:eu-central-1:kube-1"]
                                           :is_client_confidential true
                                           :scopes                 [{:resource_type_id "resource"
                                                                     :scope_id         "scope"}]}}
        context          {:configuration test-config
                          :tokeninfo     robot-token}]
    (with-db [db]
             (testing "404 when application does not exist"
               (let [unknown-application {:application_id "unknown-application"}
                     response            (api/read-application unknown-application {} db)]
                 (same! 404 (:status response))))

             (testing "created application is stored and can be retrieved, updated and deleted"
               (with-redefs [api/require-scopes (constantly nil)
                             api/require-app    (constantly {})]
                 (do (let [response (api/create-or-update-application test-application context db)]
                       (same! 200 (:status response)))

                     (let [response (api/read-application test-application {} db)]
                       (same! 200 (:status response))
                       (same! #{"a-bucket"} (:s3_buckets (:body response)))
                       (same! #{"aws:123123123123:eu-central-1:kube-1" "aws:231231231231:eu-central-1:kube-1"} (:kubernetes_clusters (:body response)))
                       (true! (:is_client_confidential (:body response)))
                       (same! #{{:resource_type_id "resource" :scope_id "scope"}} (:scopes (:body response))))

                     (let [no-buckets (assoc test-application :application (assoc (:application test-application) :s3_buckets [] :kubernetes_clusters []))
                           response   (api/create-or-update-application no-buckets context db)]
                       (same! 200 (:status response)))

                     (let [response (api/read-application test-application {} db)]
                       (same! 200 (:status response))
                       (same! #{} (:s3_buckets (:body response)))
                       (same! #{} (:kubernetes_clusters (:body response)))
                       (true! (:is_client_confidential (:body response))))

                     (let [response (api/delete-application test-application {} db)]
                       (same! 200 (:status response)))

                     (let [response (api/read-application test-application {} db)]
                       (same! 404 (:status response)))))))))

(deftest require-write-authorization
  (testing "a human needs to be in correct team"
    (let [request {:configuration test-config
                   :tokeninfo     human-token}]
      (with-redefs [auth/get-auth (constantly true)]
        (api/require-write-access-for "dogs" request))))

  (testing "a human cannot write without proper team membership"
    (let [request {:configuration test-config
                   :tokeninfo     human-token}]
      (with-redefs [auth/get-auth (constantly false)]
        (try
          (api/require-write-access-for "dogs" request)
          (is false)
          (catch Exception ex
            (same! 403 (:http-code (ex-data ex))))))))

  (testing "a robot needs to be in correct team and have write scope"
    (let [request {:configuration test-config
                   :tokeninfo     robot-token}]
      (with-redefs [auth/get-auth (constantly true)]
        (api/require-write-access-for "dogs" request))))

  (testing "a robot cannot write without scope"
    (let [request {:configuration test-config
                   :tokeninfo     (assoc robot-token "scope" ["uid"])}]
      (with-redefs [auth/get-auth (constantly true)]
        (try
          (api/require-write-access-for "dogs" request)
          (is false)
          (catch Exception ex
            (same! 403 (:http-code (ex-data ex))))))))

  (testing "a robot cannot write without proper team membership"
    (let [request {:configuration test-config
                   :tokeninfo     robot-token}]
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

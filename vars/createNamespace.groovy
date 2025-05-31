import com.sony.sie.cicd.helpers.utilities.JenkinsHelperException
import com.sony.sie.cicd.helpers.utilities.JenkinsUtils
import com.sony.sie.cicd.cd.utilities.HelmKubeHelper
import com.sony.sie.cicd.cd.utilities.HelmUtils

def call(def pipelineDefinition) {
    jenkinsUtils = new JenkinsUtils()
    timestamps {
        try {
            inputSettings()
            conf = [:]
            conf.clusterId = params.CLUSTER_ID ? params.CLUSTER_ID.trim() : ""
            conf.namespace = params.NAMESPACE ? params.NAMESPACE.trim() : ""
            if (conf.clusterId == "") throw new JenkinsHelperException("The CLUSTER_ID was not provided, please input.")
            if (conf.namespace == "") throw new JenkinsHelperException("The NAMESPACE was not provided, please input.")

            jenkinsUtils.kmjNode(templateType: 'helm', clusterList: ["${conf.clusterId}"]) {
                boolean createNamespace = params.ACTION == 'CREATE NEW NAMESPACE'
                boolean deleteNamespace = params.ACTION == 'DELETE NAMESPACE' || params.CREATE_NEW_NAMESPACE == 'DELETES EXISTING NAMESPACE'
                HelmUtils helmUtils = new HelmUtils()
                HelmKubeHelper helmKubeHelper = new HelmKubeHelper(conf.clusterId, conf.namespace)
                stage("Deletion") {
                    if (helmKubeHelper.ifNamespaceExist(conf.namespace)) {
                        if (deleteNamespace) {
                            helmUtils.deleteNamespace(conf)
                            if(createNamespace) sleep 60
                        } else {
                            createNamespace = true
                        }
                    }
                }

                if (createNamespace) {
                    stage("Creation") {
                        helmUtils.createNamespace(conf)
                    }
                } else if(params.ACTION == 'CREATE NEW NAMESPACE') {
                    echo "The namespace exists already!"
                }

            }
            currentBuild.description = "Namespace: ${conf.namespace}"
        } catch (Exception err) {
            if (!jenkinsUtils.isBuildAborted()) {
                String msg = err.getMessage()
                if (!msg) msg = 'Unknown error!'
                currentBuild.result = "FAILURE"
                if(params.ACTION == 'CREATE NEW NAMESPACE') {
                    echo "CREATE NEW NAMESPACE failed: " + msg
                    currentBuild.description = "CREATE NEW NAMESPACE failed"
                } else {
                    echo "delete namespace failed: " + msg
                    currentBuild.description = "Delete namespace failed"
                }
            }
        }
    }
}

def inputSettings() {
    def settings = [
            choice(
                    choices: ["CREATE NEW NAMESPACE", "DELETE NAMESPACE"].join("\n"),
                    description: 'Select an action to create or delete namespace',
                    name: 'ACTION'
            ),
            string(
                    defaultValue: '',
                    description: 'Required: namespace, i.e.: catalyst-test',
                    name: 'NAMESPACE',
                    trim: true
            ),
            string(
                    defaultValue: 'kc8b8be086',
                    description: 'Required: cluster id, i.e.: kc8b8be086',
                    name: 'CLUSTER_ID',
                    trim: true
            )
    ]
    def choiceList = "\'KEEPS EXISTING NAMESPACE\', \'DELETES EXISTING NAMESPACE\'"
    settings.add([$class: 'CascadeChoiceParameter',
                  name: 'CREATE_NEW_NAMESPACE',
                  description: 'The option of the namespace creation',
                  choiceType: 'PT_SINGLE_SELECT',
                  filterLength: 1,
                  filterable: false,
                  randomName: 'choice-parameter-3',
                  referencedParameters: 'ACTION',
                  script: [$class: 'GroovyScript', fallbackScript: [classpath: [], sandbox: true, script: "return [' NOT APPLICABLE ']"],
                           script: [classpath: [], sandbox: true, script: """
            if (ACTION.equals("CREATE NEW NAMESPACE")) {
                return [${choiceList}]
            } else {
                return ['NOT APPLICABLE']
            }"""
       ]]
    ])
    properties([
            buildDiscarder(
            logRotator(
                    artifactDaysToKeepStr: '',
                    artifactNumToKeepStr: '',
                    daysToKeepStr: '60',
                    numToKeepStr: '50'
            )
    ),
            parameters(settings),
            pipelineTriggers([])
    ])
    echo """
        params.ACTION: ${params.ACTION}
        params.NAMESPACE: ${params.NAMESPACE}
        params.CLUSTER_ID: ${params.CLUSTER_ID}
        params.CREATE_NEW_NAMESPACE: ${params.CREATE_NEW_NAMESPACE}
    """
}

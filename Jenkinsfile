node {
    stage ('Clone') {
        checkout scm
    }
    
    stage ('Static analysis'){
        sh 'rm -rf target/idea_inspections'
        docker.image('inponomarev/intellij-idea-analyzer').inside {
           sh 'mkdir -p ${WORKSPACE}/?/${IDEA_CONFIG_DIR}/config/options'
           sh 'ln -sf /opt/idea/jdk.table.xml ${WORKSPACE}/?/${IDEA_CONFIG_DIR}/config/options/jdk.table.xml'
           sh 'mkdir -p target/idea_inspections'
           sh '/opt/idea/bin/inspect.sh $(pwd) $(pwd)/.idea/inspectionProfiles/Project_Default.xml $(pwd)/target/idea_inspections -v2'
        }
        recordIssues(
           tools: [ideaInspection(pattern: 'target/idea_inspections/*.xml')]
        )
    }
}

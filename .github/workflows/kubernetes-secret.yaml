on:
  push:
    branches: [ "main" ]

jobs:
  deploy-and-test:
    runs-on: ubuntu-latest

    steps:
    # Step 1: Pull the code from your repository into the runner environment
    - name: Checkout Code
      uses: actions/checkout@v4

    # Step 2: Spin up a miniature local Kubernetes cluster (Minikube) inside the GitHub server
    - name: Start Local Kubernetes Cluster
      uses: medyagh/setup-minikube@master

    # Step 3: Securely generate the Kubernetes secret using the value hidden in GitHub Settings
    - name: Create Kubernetes Secret
      run: |
        kubectl create secret generic db-vault --from-literal=password="${{ secrets.DB_PASSWORD }}"

"use strict";

// Similar to the IOU creation modal - see createOptionModal.js for comments.
angular.module('demoAppModule').controller('TransferModalCtrl', function ($http, $uibModalInstance, $uibModal, apiBaseURL, peers, id) {
    const transferModal = this;

    transferModal.peers = peers;
    transferModal.id = id;
    transferModal.form = {};
    transferModal.formError = false;

    transferModal.transfer = () => {
        if (invalidFormInput()) {
            transferModal.formError = true;
        } else {
            transferModal.formError = false;

            const id = transferModal.id;
            const party = transferModal.form.counterparty;

            $uibModalInstance.close();

            const issueOptionEndpoint =
                apiBaseURL +
                `trade-option?id=${id}&party=${party}`;

            $http.get(issueOptionEndpoint).then(
                (result) => transferModal.displayMessage(result),
                (result) => transferModal.displayMessage(result)
            );
        }
    };

    transferModal.displayMessage = (message) => {
        const transferMsgModal = $uibModal.open({
            templateUrl: 'transferMsgModal.html',
            controller: 'transferMsgModalCtrl',
            controllerAs: 'transferMsgModal',
            resolve: { message: () => message }
        });

        transferMsgModal.result.then(() => {}, () => {});
    };

    transferModal.cancel = () => $uibModalInstance.dismiss();

    function invalidFormInput() {
        return transferModal.form.counterparty === undefined;
    }
});

angular.module('demoAppModule').controller('transferMsgModalCtrl', function ($uibModalInstance, message) {
    const transferMsgModal = this;
    transferMsgModal.message = message.data;
});
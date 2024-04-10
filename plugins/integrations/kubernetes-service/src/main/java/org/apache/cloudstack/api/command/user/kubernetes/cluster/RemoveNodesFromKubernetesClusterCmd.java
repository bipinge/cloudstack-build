package org.apache.cloudstack.api.command.user.kubernetes.cluster;

import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.NetworkRuleConflictException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.kubernetes.cluster.KubernetesClusterEventTypes;
import com.cloud.kubernetes.cluster.KubernetesClusterService;
import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiCommandResourceType;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseAsyncCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.KubernetesClusterResponse;
import org.apache.cloudstack.api.response.UserVmResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import java.util.List;

@APICommand(name = "removeNodesFromKubernetesCluster",
        description = "Removes external nodes from a CKS cluster. ",
        responseObject = KubernetesClusterResponse.class,
        since = "4.20.0",
        authorized = {RoleType.Admin, RoleType.ResourceAdmin, RoleType.DomainAdmin, RoleType.User})
public class RemoveNodesFromKubernetesClusterCmd extends BaseAsyncCmd {

    @Inject
    public KubernetesClusterService kubernetesClusterService;

    protected static final Logger LOGGER = Logger.getLogger(RemoveNodesFromKubernetesClusterCmd.class);

    @Parameter(name = ApiConstants.NODE_IDS,
            type = CommandType.LIST,
            collectionType = CommandType.UUID,
            entityType= UserVmResponse.class,
            description = "comma separated list of node (physical or virtual machines) IDs that need to be" +
                    "removed from the Kubernetes cluster (CKS)",
            required = true,
            since = "4.20.0")
    private List<Long> nodeIds;

    @Parameter(name = ApiConstants.ID, type = CommandType.UUID, required = true,
            entityType = KubernetesClusterResponse.class,
            description = "the ID of the Kubernetes cluster", since = "4.20.0")
    private Long clusterId;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public List<Long> getNodeIds() {
        return nodeIds;
    }

    public Long getClusterId() {
        return clusterId;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public String getEventType() {
        return KubernetesClusterEventTypes.EVENT_KUBERNETES_CLUSTER_NODES_REMOVE;
    }

    @Override
    public String getEventDescription() {
        return String.format("Removing %s nodes from the Kubernetes Cluster with ID: %s", nodeIds.size(), clusterId);
    }

    @Override
    public void execute() throws ResourceUnavailableException, InsufficientCapacityException, ServerApiException, ConcurrentOperationException, ResourceAllocationException, NetworkRuleConflictException {
        try {
            if (!kubernetesClusterService.removeNodesFromKubernetesCluster(this)) {
                throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, String.format("Failed to remove node(s) from Kubernetes cluster ID: %d", getClusterId()));
            }
            final KubernetesClusterResponse response = kubernetesClusterService.createKubernetesClusterResponse(getClusterId());
            response.setResponseName(getCommandName());
            setResponseObject(response);
        } catch (Exception e) {
            String err = String.format("Failed to remove node(s) from Kubernetes cluster ID: %d due to: %s", getClusterId(), e.getMessage());
            LOGGER.error(err, e);
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, err);
        }
    }

    @Override
    public long getEntityOwnerId() {
        return CallContext.current().getCallingAccount().getId();
    }

    @Override
    public ApiCommandResourceType getApiResourceType() {
        return ApiCommandResourceType.KubernetesCluster;
    }

    @Override
    public Long getApiResourceId() {
        return getClusterId();
    }
}

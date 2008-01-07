/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.axis2.clustering.tribes;

import org.apache.axis2.clustering.ClusteringConstants;
import org.apache.axis2.clustering.ClusteringFault;
import org.apache.axis2.clustering.configuration.ConfigurationClusteringCommand;
import org.apache.axis2.clustering.configuration.DefaultConfigurationManager;
import org.apache.axis2.clustering.context.ContextClusteringCommand;
import org.apache.axis2.clustering.context.DefaultContextManager;
import org.apache.axis2.clustering.context.commands.ContextClusteringCommandCollection;
import org.apache.axis2.clustering.context.commands.UpdateContextCommand;
import org.apache.axis2.clustering.control.AckCommand;
import org.apache.axis2.clustering.control.ControlCommand;
import org.apache.axis2.clustering.control.GetConfigurationResponseCommand;
import org.apache.axis2.clustering.control.GetStateResponseCommand;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.description.AxisModule;
import org.apache.axis2.description.AxisServiceGroup;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.catalina.tribes.ByteMessage;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.io.XByteBuffer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ChannelListener implements org.apache.catalina.tribes.ChannelListener {
    private static final Log log = LogFactory.getLog(ChannelListener.class);

    private DefaultContextManager contextManager;
    private DefaultConfigurationManager configurationManager;
    private TribesControlCommandProcessor controlCommandProcessor;
    private ChannelSender channelSender;

    private ConfigurationContext configurationContext;

    public ChannelListener(ConfigurationContext configurationContext,
                           DefaultConfigurationManager configurationManager,
                           DefaultContextManager contextManager,
                           TribesControlCommandProcessor controlCommandProcessor,
                           ChannelSender sender) {
        this.configurationManager = configurationManager;
        this.contextManager = contextManager;
        this.controlCommandProcessor = controlCommandProcessor;
        this.channelSender = sender;
        this.configurationContext = configurationContext;
    }

    public void setContextManager(DefaultContextManager contextManager) {
        this.contextManager = contextManager;
    }

    public void setConfigurationManager(DefaultConfigurationManager configurationManager) {
        this.configurationManager = configurationManager;
    }

    public void setConfigurationContext(ConfigurationContext configurationContext) {
        this.configurationContext = configurationContext;
    }

    public boolean accept(Serializable msg, Member sender) {
        return true;
    }

    public void messageReceived(Serializable msg, Member sender) {
        try {
            AxisConfiguration configuration = configurationContext.getAxisConfiguration();
            List classLoaders = new ArrayList();
            classLoaders.add(configuration.getSystemClassLoader());
            classLoaders.add(getClass().getClassLoader());
            for (Iterator iter = configuration.getServiceGroups(); iter.hasNext();) {
                AxisServiceGroup group = (AxisServiceGroup) iter.next();
                classLoaders.add(group.getServiceGroupClassLoader());
            }
            for (Iterator iter = configuration.getModules().values().iterator(); iter.hasNext();) {
                AxisModule module = (AxisModule) iter.next();
                classLoaders.add(module.getModuleClassLoader());
            }


            byte[] message = ((ByteMessage) msg).getMessage();
            msg = XByteBuffer.deserialize(message,
                                          0,
                                          message.length,
                                          (ClassLoader[])classLoaders.toArray(new ClassLoader[classLoaders.size()])); 
        } catch (Exception e) {
            log.error(e);
        }

        // If the system has not still been intialized, reject all incoming messages, except the
        // GetStateResponseCommand message
        if (configurationContext.
                getPropertyNonReplicable(ClusteringConstants.CLUSTER_INITIALIZED) == null
            && !(msg instanceof GetStateResponseCommand) &&
            !(msg instanceof GetConfigurationResponseCommand)) {

            log.warn("Received message before cluster initialization has been completed");
            return;
        }
        log.debug("Received message " + msg + " from " + TribesUtil.getHost(sender));
        try {
            processMessage(msg, sender);
        } catch (Exception e) {
            log.error(e);
        }
    }

    private void processMessage(Serializable msg, Member sender) throws ClusteringFault {
        //TODO: Reject duplicates that can be received due to retransmissions
        //TODO: ACK implosion?

        if (msg instanceof ContextClusteringCommand && contextManager != null) {
            ContextClusteringCommand ctxCmd = (ContextClusteringCommand) msg;
            contextManager.process(ctxCmd);

            // Sending ACKs for ContextClusteringCommandCollection or
            // UpdateContextCommand is sufficient
            if (msg instanceof ContextClusteringCommandCollection ||
                msg instanceof UpdateContextCommand) {
                AckCommand ackCmd = new AckCommand(ctxCmd.getUniqueId());

                // Send the ACK
                this.channelSender.sendToMember(ackCmd, sender);
            }
        } else if (msg instanceof ConfigurationClusteringCommand &&
                   configurationManager != null) {
            configurationManager.process((ConfigurationClusteringCommand) msg);
        } else if (msg instanceof ControlCommand && controlCommandProcessor != null) {
            controlCommandProcessor.process((ControlCommand) msg, sender);
        }
    }
}

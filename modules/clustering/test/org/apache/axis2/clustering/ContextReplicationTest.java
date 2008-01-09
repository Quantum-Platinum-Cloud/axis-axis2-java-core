/*                                                                             
 * Copyright 2004,2005 The Apache Software Foundation.                         
 *                                                                             
 * Licensed under the Apache License, Version 2.0 (the "License");             
 * you may not use this file except in compliance with the License.            
 * You may obtain a copy of the License at                                     
 *                                                                             
 *      http://www.apache.org/licenses/LICENSE-2.0                             
 *                                                                             
 * Unless required by applicable law or agreed to in writing, software         
 * distributed under the License is distributed on an "AS IS" BASIS,           
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.    
 * See the License for the specific language governing permissions and         
 * limitations under the License.                                              
 */
package org.apache.axis2.clustering;

import junit.framework.TestCase;
import org.apache.axiom.om.util.UUIDGenerator;
import org.apache.axis2.AxisFault;
import org.apache.axis2.clustering.configuration.ConfigurationManager;
import org.apache.axis2.clustering.configuration.DefaultConfigurationManager;
import org.apache.axis2.clustering.configuration.DefaultConfigurationManagerListener;
import org.apache.axis2.clustering.context.ContextManager;
import org.apache.axis2.clustering.context.DefaultContextManager;
import org.apache.axis2.clustering.context.DefaultContextManagerListener;
import org.apache.axis2.clustering.tribes.TribesClusterManager;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.ConfigurationContextFactory;
import org.apache.axis2.context.ServiceGroupContext;
import org.apache.axis2.description.AxisService;
import org.apache.axis2.description.AxisServiceGroup;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.axis2.transport.http.server.HttpUtils;

/**
 *
 */
public class ContextReplicationTest extends TestCase {

    private static final String TEST_SERVICE_NAME = "testService";

    private ClusterManager clusterManager1;
    private ContextManager ctxMan1;
    private ConfigurationManager configMan1;
    private ConfigurationContext configurationContext1;
    private AxisServiceGroup serviceGroup1;
    private AxisService service1;

    private ClusterManager clusterManager2;
    private ContextManager ctxMan2;
    private ConfigurationManager configMan2;
    private ConfigurationContext configurationContext2;
    private AxisServiceGroup serviceGroup2;
    private AxisService service2;

    protected void setUp() throws Exception {
        System.setProperty(ClusteringConstants.LOCAL_IP_ADDRESS, HttpUtils.getIpAddress());

        // First cluster
        configurationContext1 =
                ConfigurationContextFactory.createDefaultConfigurationContext();
        serviceGroup1 = createAxisServiceGroup(configurationContext1);
        service1 = createAxisService(serviceGroup1);
        ctxMan1 = getContextManager();
        configMan1 = getConfigurationManager();
        clusterManager1 = getClusterManager(configurationContext1, ctxMan1, configMan1);
        clusterManager1.init();
        System.out.println("ClusterManager-1 successfully initialized");

        // Second cluster
        configurationContext2 =
                ConfigurationContextFactory.createDefaultConfigurationContext();
        serviceGroup2 = createAxisServiceGroup(configurationContext2);
        service2 = createAxisService(serviceGroup2);
        ctxMan2 = getContextManager();
        configMan2 = getConfigurationManager();
        clusterManager2 = getClusterManager(configurationContext2, ctxMan2, configMan2);
        clusterManager2.init();
        System.out.println("ClusterManager-2 successfully initialized");
    }

    protected ClusterManager getClusterManager(ConfigurationContext configCtx,
                                               ContextManager contextManager,
                                               ConfigurationManager configManager)
            throws AxisFault {
        ClusterManager clusterManager = new TribesClusterManager();
        configCtx.getAxisConfiguration().setClusterManager(clusterManager);

        configManager.
                setConfigurationManagerListener(new DefaultConfigurationManagerListener());
        clusterManager.setConfigurationManager(configManager);

        contextManager.setContextManagerListener(new DefaultContextManagerListener());
        clusterManager.setContextManager(contextManager);

        clusterManager.setConfigurationContext(configCtx);

        return clusterManager;
    }

    protected AxisServiceGroup createAxisServiceGroup(ConfigurationContext configCtx)
            throws AxisFault {
        AxisConfiguration axisConfig = configCtx.getAxisConfiguration();
        AxisServiceGroup serviceGroup = new AxisServiceGroup(axisConfig);
        axisConfig.addServiceGroup(serviceGroup);
        return serviceGroup;
    }

    protected AxisService createAxisService(AxisServiceGroup serviceGroup) throws AxisFault {
        AxisService service = new AxisService(TEST_SERVICE_NAME);
        serviceGroup.addService(service);
        return service;
    }

    protected ContextManager getContextManager() throws AxisFault {
        ContextManager contextManager = new DefaultContextManager();
        contextManager.setContextManagerListener(new DefaultContextManagerListener());
        return contextManager;
    }

    protected ConfigurationManager getConfigurationManager() throws AxisFault {
        ConfigurationManager contextManager = new DefaultConfigurationManager();
        contextManager.setConfigurationManagerListener(new DefaultConfigurationManagerListener());
        return contextManager;
    }

    public void testSetPropertyInConfigurationContext() throws Exception {
        {
            String key1 = "configCtxKey";
            String val1 = "configCtxVal1";
            configurationContext1.setProperty(key1, val1);
            ctxMan1.updateContext(configurationContext1);
            Thread.sleep(1000); // Give some time for the replication to take place

            String value = (String) configurationContext2.getProperty(key1);
            assertEquals(val1, value);
        }

        {
            String key2 = "configCtxKey2";
            String val2 = "configCtxVal1";
            configurationContext2.setProperty(key2, val2);
            ctxMan2.updateContext(configurationContext2);
            Thread.sleep(1000); // Give some time for the replication to take place

            String value = (String) configurationContext1.getProperty(key2);
            assertEquals(val2, value);
        }
    }

    public void testRemovePropertyFromConfigurationContext() throws Exception {
        String key1 = "configCtxKey";
        String val1 = "configCtxVal1";

        // First set the property on a cluster 1 and replicate it
        {
            configurationContext1.setProperty(key1, val1);
            ctxMan1.updateContext(configurationContext1);
            Thread.sleep(1000); // Give some time for the replication to take place

            String value = (String) configurationContext2.getProperty(key1);
            assertEquals(val1, value);
        }

        // Next remove this property from cluster 2, replicate it, and check that it is unavailable in cluster 1
        configurationContext2.removeProperty(key1);
        ctxMan2.updateContext(configurationContext2);
        Thread.sleep(1000); // Give some time for the replication to take place

        String value = (String) configurationContext1.getProperty(key1);
        assertNull(configurationContext2.getProperty(key1));
        assertNull(value);
    }

    public void testSetPropertyInServiceGroupContext() throws Exception {
//        String sgcID = UUIDGenerator.getUUID();

        ServiceGroupContext serviceGroupContext1 =
                configurationContext1.createServiceGroupContext(serviceGroup1);
        serviceGroupContext1.setId(TEST_SERVICE_NAME);
        configurationContext1.addServiceGroupContextIntoApplicationScopeTable(serviceGroupContext1);
        assertNotNull(serviceGroupContext1);

        ServiceGroupContext serviceGroupContext2 =
                configurationContext2.createServiceGroupContext(serviceGroup2);
        serviceGroupContext2.setId(TEST_SERVICE_NAME);
        configurationContext2.addServiceGroupContextIntoApplicationScopeTable(serviceGroupContext2);
        assertNotNull(serviceGroupContext2);

        String key1 = "sgCtxKey";
        String val1 = "sgCtxVal1";
        serviceGroupContext1.setProperty(key1, val1);
        ctxMan1.updateContext(serviceGroupContext1);

        Thread.sleep(1000);
        assertEquals(val1, serviceGroupContext2.getProperty(key1));
    }

    public void testSetPropertyInServiceGroupContext2() throws Exception {
        String sgcID = UUIDGenerator.getUUID();

        ServiceGroupContext serviceGroupContext1 =
                configurationContext1.createServiceGroupContext(serviceGroup1);
        serviceGroupContext1.setId(sgcID);
        configurationContext1.addServiceGroupContextIntoSoapSessionTable(serviceGroupContext1);
        assertNotNull(serviceGroupContext1);

        ServiceGroupContext serviceGroupContext2 =
                configurationContext2.createServiceGroupContext(serviceGroup2);
        serviceGroupContext2.setId(sgcID);
        configurationContext2.addServiceGroupContextIntoSoapSessionTable(serviceGroupContext2);
        assertNotNull(serviceGroupContext2);

        String key1 = "sgCtxKey";
        String val1 = "sgCtxVal1";
        serviceGroupContext1.setProperty(key1, val1);
        ctxMan1.updateContext(serviceGroupContext1);

        Thread.sleep(1000);
        assertEquals(val1, serviceGroupContext2.getProperty(key1));
    }

    protected void tearDown() throws Exception {
        super.tearDown();
        clusterManager1.shutdown();
        clusterManager2.shutdown();
    }
    /*public void test2() {

    }*/
}
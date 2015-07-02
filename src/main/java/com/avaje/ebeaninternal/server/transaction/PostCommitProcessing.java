package com.avaje.ebeaninternal.server.transaction;

import com.avaje.ebean.annotation.IndexEvent;
import com.avaje.ebeaninternal.api.SpiTransaction;
import com.avaje.ebeaninternal.api.TransactionEvent;
import com.avaje.ebeaninternal.api.TransactionEventTable;
import com.avaje.ebeaninternal.api.TransactionEventTable.TableIUD;
import com.avaje.ebeaninternal.elastic.IndexUpdates;
import com.avaje.ebeaninternal.server.cluster.ClusterManager;
import com.avaje.ebeaninternal.server.core.PersistRequestBean;
import com.avaje.ebeaninternal.server.deploy.BeanDescriptorManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Performs post commit processing using a background thread.
 * <p>
 * This includes Cluster notification, and BeanPersistListeners.
 * </p>
 */
public final class PostCommitProcessing {

  private static final Logger logger = LoggerFactory.getLogger(PostCommitProcessing.class);

  private final ClusterManager clusterManager;

  private final TransactionEvent event;

  private final String serverName;

  private final TransactionManager manager;

  private final List<PersistRequestBean<?>> persistBeanRequests;

  private final BeanPersistIdMap beanPersistIdMap;

  private final RemoteTransactionEvent remoteTransactionEvent;

  private final DeleteByIdMap deleteByIdMap;

  private final IndexEvent txnIndexMode;

  private final int txnIndexBulkBatchSize;

  /**
   * Create for an external modification.
   */
  public PostCommitProcessing(ClusterManager clusterManager, TransactionManager manager, TransactionEvent event) {

    this.clusterManager = clusterManager;
    this.manager = manager;
    this.serverName = manager.getServerName();
    this.txnIndexMode = IndexEvent.IGNORE;
    this.txnIndexBulkBatchSize = 0;
    this.event = event;
    this.deleteByIdMap = event.getDeleteByIdMap();
    this.persistBeanRequests = event.getPersistRequestBeans();
    this.beanPersistIdMap = createBeanPersistIdMap();
    this.remoteTransactionEvent = createRemoteTransactionEvent();
  }

  /**
   * Create for a transaction.
   */
  public PostCommitProcessing(ClusterManager clusterManager, TransactionManager manager, SpiTransaction transaction) {

    this.clusterManager = clusterManager;
    this.manager = manager;
    this.serverName = manager.getServerName();
    this.txnIndexMode = transaction.getIndexUpdateMode();
    this.txnIndexBulkBatchSize = transaction.getIndexBulkBatchSize();
    this.event = transaction.getEvent();
    this.deleteByIdMap = event.getDeleteByIdMap();
    this.persistBeanRequests = event.getPersistRequestBeans();
    this.beanPersistIdMap = createBeanPersistIdMap();
    this.remoteTransactionEvent = createRemoteTransactionEvent();
  }

  public void notifyLocalCacheIndex() {

    // notify cache with bulk insert/update/delete statements
    processTableEvents(event.getEventTables());

    // notify cache with bean changes
    event.notifyCache();
  }

  /**
   * Table events are where SQL or external tools are used. In this case the
   * cache is notified based on the table name (rather than bean type).
   */
  private void processTableEvents(TransactionEventTable tableEvents) {

    if (tableEvents != null && !tableEvents.isEmpty()) {
      // notify cache with table based changes
      BeanDescriptorManager dm = manager.getBeanDescriptorManager();
      for (TableIUD tableIUD : tableEvents.values()) {
        dm.cacheNotify(tableIUD);
      }
    }
  }

  /**
   * Process any ElasticSearch index updates.
   */
  protected void processIndexUpdates() {

    if (isIndexUpdateTransaction()) {
      // collect 'bulk update' and 'queue' events
      IndexUpdates indexUpdates = new IndexUpdates();
      event.addToIndexUpdates(indexUpdates);
      if (deleteByIdMap != null) {
        deleteByIdMap.addToIndexUpdates(indexUpdates, txnIndexMode);
      }

      if (!indexUpdates.isEmpty()) {
        // send to ElasticSearch Bulk API and/or queue
        manager.processIndexUpdates(indexUpdates, txnIndexBulkBatchSize);
      }
    }
  }

  /**
   * Return true if updates to the Elastic search index for occur for this transaction.
   */
  private boolean isIndexUpdateTransaction() {
    return manager.isElasticActive() && (txnIndexMode == null || txnIndexMode != IndexEvent.IGNORE);
  }

  public void notifyCluster() {
    if (remoteTransactionEvent != null && !remoteTransactionEvent.isEmpty()) {
      // send the interesting events to the cluster
      if (logger.isDebugEnabled()) {
        logger.debug("Cluster Send: {}", remoteTransactionEvent);
      }

      clusterManager.broadcast(remoteTransactionEvent);
    }
  }

  public Runnable notifyPersistListeners() {
    return new Runnable() {
      public void run() {
        localPersistListenersNotify();
        processIndexUpdates();
      }
    };
  }

  private void localPersistListenersNotify() {
    if (persistBeanRequests != null) {
      for (int i = 0; i < persistBeanRequests.size(); i++) {
        persistBeanRequests.get(i).notifyLocalPersistListener();
      }
    }
    TransactionEventTable eventTables = event.getEventTables();
    if (eventTables != null && !eventTables.isEmpty()) {
      BulkEventListenerMap map = manager.getBulkEventListenerMap();
      for (TableIUD tableIUD : eventTables.values()) {
        map.process(tableIUD);
      }
    }
  }

  private BeanPersistIdMap createBeanPersistIdMap() {

    if (persistBeanRequests == null) {
      return null;
    }

    BeanPersistIdMap m = new BeanPersistIdMap();
    for (int i = 0; i < persistBeanRequests.size(); i++) {
      persistBeanRequests.get(i).addToPersistMap(m);
    }
    return m;
  }

  private RemoteTransactionEvent createRemoteTransactionEvent() {

    if (!clusterManager.isClustering()) {
      return null;
    }

    RemoteTransactionEvent remoteTransactionEvent = new RemoteTransactionEvent(serverName);

    if (beanPersistIdMap != null) {
      for (BeanPersistIds beanPersist : beanPersistIdMap.values()) {
        remoteTransactionEvent.addBeanPersistIds(beanPersist);
      }
    }

    if (deleteByIdMap != null) {
      remoteTransactionEvent.setDeleteByIdMap(deleteByIdMap);
    }

    TransactionEventTable eventTables = event.getEventTables();
    if (eventTables != null && !eventTables.isEmpty()) {
      for (TableIUD tableIUD : eventTables.values()) {
        remoteTransactionEvent.addTableIUD(tableIUD);
      }
    }

    return remoteTransactionEvent;
  }

}

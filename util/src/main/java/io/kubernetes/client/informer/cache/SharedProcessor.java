/*
Copyright 2020 The Kubernetes Authors.
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package io.kubernetes.client.informer.cache;

import io.kubernetes.client.common.KubernetesObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.apache.commons.collections4.CollectionUtils;
import org.joda.time.DateTime;

/*
 * SharedProcessor class manages all the registered ProcessorListener and distributes notifications.
 */
public class SharedProcessor<ApiType extends KubernetesObject> {

  private ReadWriteLock lock = new ReentrantReadWriteLock();

  private List<ProcessorListener<ApiType>> listeners;
  private List<ProcessorListener<ApiType>> syncingListeners;

  private ExecutorService executorService;

  public SharedProcessor() {
    this(Executors.newCachedThreadPool());
  }

  public SharedProcessor(ExecutorService threadPool) {
    this.listeners = new ArrayList<>();
    this.syncingListeners = new ArrayList<>();

    this.executorService = threadPool;
  }

  /**
   * addAndStartListener first adds the specific processorListener then starts the listener with
   * executor.
   *
   * @param processorListener specific processor listener
   */
  public void addAndStartListener(final ProcessorListener<ApiType> processorListener) {
    lock.writeLock().lock();
    try {
      addListenerLocked(processorListener);

      executorService.execute(processorListener);
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * addListener adds the specific processorListener, but not start it.
   *
   * @param processorListener specific processor listener
   */
  public void addListener(final ProcessorListener<ApiType> processorListener) {
    lock.writeLock().lock();
    try {
      addListenerLocked(processorListener);
    } finally {
      lock.writeLock().unlock();
    }
  }

  private void addListenerLocked(final ProcessorListener<ApiType> processorListener) {
    this.listeners.add(processorListener);
    this.syncingListeners.add(processorListener);
  }

  /** starts the processor listeners. */
  public void run() {
    lock.readLock().lock();
    try {
      if (CollectionUtils.isEmpty(listeners)) {
        return;
      }
      for (ProcessorListener listener : listeners) {
        executorService.execute(listener);
      }
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * distribute the object among listeners.
   *
   * @param obj specific obj
   * @param isSync is sync or not
   */
  public void distribute(ProcessorListener.Notification<ApiType> obj, boolean isSync) {
    lock.readLock().lock();
    try {
      if (isSync) {
        for (ProcessorListener<ApiType> listener : syncingListeners) {
          listener.add(obj);
        }
      } else {
        for (ProcessorListener<ApiType> listener : listeners) {
          listener.add(obj);
        }
      }
    } finally {
      lock.readLock().unlock();
    }
  }

  public boolean shouldResync() {
    lock.writeLock().lock();
    boolean resyncNeeded = false;
    try {
      this.syncingListeners = new ArrayList<>(this.listeners.size());

      DateTime now = DateTime.now();
      for (ProcessorListener listener : this.listeners) {
        if (listener.shouldResync(now)) {
          resyncNeeded = true;
          this.syncingListeners.add(listener);
          listener.determineNextResync(now);
        }
      }
    } finally {
      lock.writeLock().unlock();
    }
    return resyncNeeded;
  }

  public void stop() {
    lock.writeLock().lock();
    try {
      listeners = null;
    } finally {
      lock.writeLock().unlock();
    }
    // TODO(yue9944882): gracefully shutdown listener pool
    executorService.shutdownNow();
  }
}

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.locks.Lock;

public class ReentrantLock {

  /**
   * Реализовал асинхронную обработку пользовательской очереди с гарантированной потокобезопасностью,
   * используя блокировки(ReentrantLock) для синхронизации доступа к данным,
   * а также управление транзакциями с помощью Spring @Transactional
   **/
  @Transactional
  public void asyncProcessUserQueue(BlockingQueue<PersonalList> queue, long userId) {
    while (!queue.isEmpty()) {
      userLocks.computeIfAbsent(userId, k -> new java.util.concurrent.locks.ReentrantLock());
      Lock lock = userLocks.get(userId);
      lock.lock();
      PersonalList personalList = queue.poll();
      try {
        if (personalList != null) {
          long customListId = savePersonalList(personalList, userId);
          publishEvent(customListId, personalList.getName(), userId, true, null);
        }
      } catch (UninterceptableException e) {
        publishEvent(personalList.getId(), personalList.getName(), userId, false, e.getMessage());
      } catch (Exception e) {
        LOG.error(e, "Error during saving personal list with id: %d, for user with id: %d", personalList.getId(), userId);
        publishEvent(personalList.getId(), personalList.getName(), userId, false, e.getMessage());
      } finally {
        lock.unlock();
      }
    }
  }
}

package util.module;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @class public class ConcurrentCyclicFIFO<E>
 * @brief Concurrent Cyclic FIFO queue class
 */
public class ConcurrentCyclicFIFO<E> {

    static class Node<E> {
        volatile E item;
        Node<E> next;

        Node(E x) {
            item = x;
        }
    }

    /** Current number of elements */
    private final AtomicInteger count = new AtomicInteger(0);

    /** Head of linked list */
    private transient Node<E> head;

    /** Tail of linked list */
    private transient Node<E> last;

    /** Lock held by take, poll, etc */
    private final ReentrantLock takeLock = new ReentrantLock();

    /** Wait queue for waiting takes */
    private final Condition notEmpty = takeLock.newCondition();

    /** Lock held by put, offer, etc */
    private final ReentrantLock putLock = new ReentrantLock();

    /**
     * Signals a waiting take. Called only from put/offer (which do not
     * otherwise ordinarily lock takeLock.)
     */
    private void signalNotEmpty() {
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lock();
        try {
            notEmpty.signal();
        } finally {
            takeLock.unlock();
        }
    }

    /**
     * Creates a node and links it at end of queue.
     *
     * @param x
     *            the item
     */
    private void insert(Node<E> x) {
        last = last.next = x;
    }

    /**
     * Removes a node from head of queue,
     *
     * @return the node
     */
    private Node<E> extract() {
        Node<E> current = head;
        head = head.next;

        current.item = head.item;
        head.item = null;

        return current;
    }

    public ConcurrentCyclicFIFO() {
        last = head = new Node<E>(null);
    }

    public int size() {
        return count.get();
    }

    public boolean offer(E e) {
        if (e == null) {
            throw new NullPointerException();
        }

        final AtomicInteger count = this.count;

        boolean shouldSignal = false;
        final ReentrantLock putLock = this.putLock;
        putLock.lock();
        try {
            insert(new Node<E>(e));
            shouldSignal = (count.getAndIncrement() == 0);
        } finally {
            putLock.unlock();
        }

        if (shouldSignal) {
            signalNotEmpty();
        }
        return !shouldSignal;
    }

    public E take() throws InterruptedException {
        Node<E> x;
        final AtomicInteger count = this.count;
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lockInterruptibly();
        try {
            try {
                while (count.get() == 0)
                    notEmpty.await();
            } catch (InterruptedException ie) {
                // propagate to a non-interrupted thread
                notEmpty.signal();
                throw ie;
            }

            x = extract();
            if (count.getAndDecrement() > 1) {
                notEmpty.signal();
            }
        } finally {
            takeLock.unlock();
        }

        E result = x.item;

        // temporary clearence
        x.item = null;
        x.next = null;

        return result;
    }

    public E poll() {
        final AtomicInteger count = this.count;
        if (count.get() == 0) {
            return null;
        }

        Node<E> x = null;
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lock();

        try {
            if (count.get() > 0) {
                x = extract();
                if (count.getAndDecrement() > 1) {
                    notEmpty.signal();
                }
            }
        } finally {
            takeLock.unlock();
        }

        if (x != null) {
            E result = x.item;

            // temporary clearence
            x.item = null;
            x.next = null;

            return result;
        }

        return null;
    }

    public void clear() {
        putLock.lock();
        takeLock.lock();

        try {
            head.next = null;
            assert head.item == null;
            last = head;
            count.set(0);
        } finally {
            takeLock.unlock();
            putLock.unlock();
        }
    }
}

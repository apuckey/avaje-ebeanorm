package com.avaje.ebean.event;

import com.avaje.ebean.EbeanServer;
import com.avaje.ebean.EbeanServerFactory;
import com.avaje.ebean.bean.BeanCollection;
import com.avaje.ebean.common.BeanList;
import com.avaje.ebean.config.ServerConfig;
import com.avaje.tests.model.basic.EBasic;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BeanFindControllerTest {

  @Test
  public void test() {

    ServerConfig config = new ServerConfig();

    config.setName("h2other");
    config.loadFromProperties();
    config.setRegister(false);
    config.setDefaultServer(false);
    config.getClasses().add(EBasic.class);

    EBasicFindController findController = new EBasicFindController();
    config.getFindControllers().add(findController);

    EbeanServer ebeanServer = EbeanServerFactory.create(config);

    assertFalse(findController.calledInterceptFind);
    ebeanServer.find(EBasic.class, 42);
    assertTrue(findController.calledInterceptFind);

    findController.findIntercept = true;
    EBasic eBasic = ebeanServer.find(EBasic.class, 42);

    assertEquals(Integer.valueOf(47), eBasic.getId());
    assertEquals("47", eBasic.getName());

    assertFalse(findController.calledInterceptFindMany);

    List<EBasic> list = ebeanServer.find(EBasic.class).where().eq("name", "AnInvalidNameSoEmpty").findList();
    assertEquals(0, list.size());
    assertTrue(findController.calledInterceptFindMany);

    findController.findManyIntercept = true;

    list = ebeanServer.find(EBasic.class).where().eq("name", "AnInvalidNameSoEmpty").findList();
    assertEquals(1, list.size());

    eBasic = list.get(0);
    assertEquals(Integer.valueOf(47), eBasic.getId());
    assertEquals("47", eBasic.getName());
  }

  static class EBasicFindController implements BeanFindController {

    boolean findIntercept;
    boolean findManyIntercept;
    boolean calledInterceptFind;
    boolean calledInterceptFindMany;

    @Override
    public boolean isRegisterFor(Class<?> cls) {
      return EBasic.class.equals(cls);
    }

    @Override
    public boolean isInterceptFind(BeanQueryRequest<?> request) {
      calledInterceptFind = true;
      return findIntercept;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T find(BeanQueryRequest<T> request) {
      return (T)createBean();
    }

    @Override
    public boolean isInterceptFindMany(BeanQueryRequest<?> request) {
      calledInterceptFindMany = true;
      return findManyIntercept;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> BeanCollection<T> findMany(BeanQueryRequest<T> request) {

      BeanList<T> list = new BeanList<T>();
      list.add((T)createBean());
      return list;
    }
  }

  private static EBasic createBean() {
    EBasic b = new EBasic();
    b.setId(47);
    b.setName("47");
    return b;
  }
}
/**
 * Software License Declaration.
 * <p>
 * wandaph.com, Co,. Ltd.
 * Copyright ? 2017 All Rights Reserved.
 * <p>
 * Copyright Notice
 * This documents is provided to wandaph contracting agent or authorized programmer only.
 * This source code is written and edited by wandaph Co,.Ltd Inc specially for financial
 * business contracting agent or authorized cooperative company, in order to help them to
 * install, programme or central control in certain project by themselves independently.
 * <p>
 * Disclaimer
 * If this source code is needed by the one neither contracting agent nor authorized programmer
 * during the use of the code, should contact to wandaph Co,. Ltd Inc, and get the confirmation
 * and agreement of three departments managers  - Research Department, Marketing Department and
 * Production Department.Otherwise wandaph will charge the fee according to the programme itself.
 * <p>
 * Any one,including contracting agent and authorized programmer,cannot share this code to
 * the third party without the agreement of wandaph. If Any problem cannot be solved in the
 * procedure of programming should be feedback to wandaph Co,. Ltd Inc in time, Thank you!
 */
package com.wandaph.openfeign.test.hystrix;

import com.wandaph.openfeign.test.clients.RemoteClient;
import com.wandaph.openfeign.test.dto.CommonDataResponse;
import feign.hystrix.FallbackFactory;

/**
 * @author lvzhen
 * @version Id: FallbackFactoryRemote.java, v 0.1 2019/3/12 11:27 lvzhen Exp $$
 */
public class FallbackFactoryRemote implements FallbackFactory<RemoteClient> {

    @Override
    public RemoteClient create(Throwable cause) {
        return new RemoteClient() {
            @Override
            public CommonDataResponse testDemo() {
                CommonDataResponse response = new CommonDataResponse();
                response.setMsg("Fallback.response...");
                return response;
            }
        };
    }

}
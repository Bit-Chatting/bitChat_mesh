package com.example.bitcaht.Signaling.Handler;

import com.example.bitcaht.Signaling.model.SignalType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Component
@Log4j2
public class SocketHandler extends TextWebSocketHandler {
    //채팅방에 들어간 유저 Map key = roomId, value = 유저 세션
    private Map<String, List<WebSocketSession>> users = new HashMap<>();
    //채팅방과 offer 저장
    private Map<String, String> offerMap = new HashMap<>();

    Integer maxUser = 3;

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> messageMap = new ObjectMapper().readValue(message.getPayload(), Map.class);

        /**
        let join_room = {
            type: "join_room",
            room: "roomName"
        }
        socketRef.current.send(JSON.stringify(join_room));
        */
        SignalType signalType = SignalType.valueOf((String) messageMap.get("type"));

        String roomId = (String) messageMap.get("roomName");
        switch (signalType){
            case join_room:

                //채팅방에 있는 유저 정보
                List<WebSocketSession> userSessions = null;
                if(!users.containsKey(roomId)){
                    userSessions = new CopyOnWriteArrayList<>();
                    users.put(roomId, userSessions);
                }else {
                    userSessions = users.get(roomId);
                }


                if(userSessions.size() >= maxUser){
                    session.sendMessage(new TextMessage("room_full"));
                    break;
                }

                //채팅방에 현재 세션 추가
                userSessions.add(session);
                List<String> otherUsers = userSessions.stream()
                        .filter(user -> !user.getId().equals(session.getId()))
                        .map(WebSocketSession::getId)
                        .collect(Collectors.toList());
                session.sendMessage(new TextMessage(
                        new ObjectMapper().writeValueAsString(
                                //type: all_users, data:, otherUsers
                                Map.of("type", "all_users", "data", otherUsers)
                        )
                ));
                break;

            /**
            let offer = {
                type: "offer",
                sdp: sdp,
                room: "roomName"
            }
            socketRef.current.emit(JSON.stringify(join_room));x`
            */
            case offer:
                offerMap.put(roomId, session.getId());
                //채팅방에 있는 유저 정보
                userSessions = users.get(roomId);
                for(WebSocketSession user : userSessions){
                    if(!user.getId().equals(session.getId())){
                        //채팅방에 있는 유저들에게 offer 전송
                        user.sendMessage(new TextMessage(
                                new ObjectMapper().writeValueAsString(
                                        Map.of("type", "getOffer", "data", messageMap.get("sdp"))
                                )
                        ));
                    }
                }
                break;

            case answer:
                //채팅방에 있는 유저 정보
                userSessions = users.get(roomId);
                for(WebSocketSession user : userSessions){
                    if(!user.getId().equals(session.getId())){
                        //채팅방에 있는 유저들에게 offer 전송
                        user.sendMessage(new TextMessage(
                                new ObjectMapper().writeValueAsString(
                                        Map.of("type", "getAnswer", "data", messageMap.get("sdp"))
                                )
                        ));
                    }
                }
                break;

            case candidate:
                userSessions = users.get(roomId);
                for (WebSocketSession user : userSessions) {
                    if (!user.getId().equals(session.getId())) {
                        user.sendMessage(new TextMessage(
                                new ObjectMapper().writeValueAsString(
                                        Map.of("type", "getCandidate", "data", messageMap.get("candidate")))));
                    }
                }
                break;
            default:
                log.info("시그널 타입에러");

        }
    }

    //클라이언트에서 접속 성공 시 발생하는 이벤트
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {

    }
}

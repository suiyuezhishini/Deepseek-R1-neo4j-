// ApiRequest.java
package org.example.dsdemo.dto;

import lombok.Data;
import lombok.Setter;

import java.util.List;

@Data
public class ApiRequest {
    private String model = "deepseek-reasoner";
    private List<Message> messages;
    private double temperature = 0.7;
    private int max_tokens = 5000;
    @Setter
    private List<String> fileContents;
    
    // 显式添加 setter 方法
    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }
}

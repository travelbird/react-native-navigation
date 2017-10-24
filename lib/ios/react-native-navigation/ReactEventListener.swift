//
//  ReactEventListener.swift
//  NativeNavigation
//
//  Created by Vlad Smoc on 23-10-2017.
//

class ReactEventListener {
    
    var onEvent:((_ eventName: String, _ props: [String : Any]) -> Void)?
}

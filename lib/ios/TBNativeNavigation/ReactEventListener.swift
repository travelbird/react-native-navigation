//
//  ReactEventListener.swift
//  NativeNavigation
//
//  Created by Vlad Smoc on 23-10-2017.
//

class ReactEventListener {
    
    var onEvent:((eventName: String, props: [String : Any]) -> Void)?
}
